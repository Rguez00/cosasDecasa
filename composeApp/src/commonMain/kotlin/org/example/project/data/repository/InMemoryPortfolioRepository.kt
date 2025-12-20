package org.example.project.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.example.project.data.repository.PortfolioError.*
import org.example.project.domain.model.Holding
import org.example.project.domain.model.PortfolioSnapshot
import org.example.project.domain.model.PositionSnapshot
import org.example.project.domain.model.Transaction
import org.example.project.domain.model.TransactionType
import org.example.project.presentation.state.PortfolioState

/**
 * Implementación en memoria del PortfolioRepository.
 *
 * ✅ Requisitos:
 * - Cash inicial 10.000€
 * - Validaciones quantity>0, cash suficiente, holdings suficientes
 * - Comisión 0.5%
 * - Preview buy/sell
 * - Historial + export CSV
 * - Snapshot enriquecido (positions + PnL)
 * - Actualización en tiempo real (recalcula al recibir priceUpdates del mercado)
 * - Thread-safe con Mutex
 */
class InMemoryPortfolioRepository(
    private val marketRepo: MarketRepository,
    initialCash: Double = 10_000.0,
    private val externalScope: CoroutineScope? = null
) : PortfolioRepository {

    companion object {
        private const val COMMISSION_RATE = 0.005 // 0.5%
    }

    // Scope interno (si no nos pasan uno)
    private val job = SupervisorJob()
    private val scope: CoroutineScope =
        externalScope ?: CoroutineScope(Dispatchers.Default + job)

    // Lock de consistencia
    private val mutex = Mutex()

    // Estado interno “fuente de verdad”
    private var cash: Double = initialCash
    private val holdingsMap: MutableMap<String, Holding> = linkedMapOf()
    private val transactions: MutableList<Transaction> = mutableListOf()

    // Id incremental (thread-safe porque incrementa dentro de mutex)
    private var nextTxId: Int = 1

    private val _portfolioState = MutableStateFlow(
        PortfolioState(
            cash = cash,
            holdings = emptyList(),
            transactions = emptyList(),
            portfolioValue = cash,
            pnlEuro = 0.0,
            pnlPercent = 0.0
        )
    )
    override val portfolioState: StateFlow<PortfolioState> = _portfolioState

    private var pricesJob: Job? = null

    init {
        // Recalcular “live” cuando cambian precios
        pricesJob = scope.launch {
            marketRepo.priceUpdates.collect { update ->
                mutex.withLock {
                    // Optimización: si no tenemos esa acción, no recalculamos
                    if (!holdingsMap.containsKey(update.ticker)) return@withLock
                    emitPortfolioStateLocked()
                }
            }
        }
    }

    // ============================================================
    // PREVIEWS
    // ============================================================

    override suspend fun previewBuy(ticker: String, quantity: Int): Result<TransactionPreview> {
        if (quantity <= 0) return Result.failure(InvalidQuantity(quantity))

        val price = marketRepo.getSnapshot(ticker)?.currentPrice
            ?: return Result.failure(UnknownTicker(ticker))

        val gross = price * quantity
        val commission = gross * COMMISSION_RATE
        val net = gross + commission

        val available = mutex.withLock { cash }
        if (available + 1e-9 < net) {
            return Result.failure(InsufficientCash(required = net, available = available))
        }

        return Result.success(
            TransactionPreview(
                ticker = ticker,
                quantity = quantity,
                pricePerShare = price,
                grossTotal = gross,
                commission = commission,
                netTotal = net
            )
        )
    }

    override suspend fun previewSell(ticker: String, quantity: Int): Result<TransactionPreview> {
        if (quantity <= 0) return Result.failure(InvalidQuantity(quantity))

        val owned = mutex.withLock { holdingsMap[ticker]?.quantity ?: 0 }
        if (owned < quantity) return Result.failure(InsufficientHoldings(ticker, quantity, owned))

        val price = marketRepo.getSnapshot(ticker)?.currentPrice
            ?: return Result.failure(UnknownTicker(ticker))

        val gross = price * quantity
        val commission = gross * COMMISSION_RATE
        val net = gross - commission

        return Result.success(
            TransactionPreview(
                ticker = ticker,
                quantity = quantity,
                pricePerShare = price,
                grossTotal = gross,
                commission = commission,
                netTotal = net
            )
        )
    }

    // ============================================================
    // BUY / SELL (confirmadas)
    // ============================================================

    override suspend fun buy(ticker: String, quantity: Int): Result<Transaction> {
        if (quantity <= 0) return Result.failure(InvalidQuantity(quantity))

        val price = marketRepo.getSnapshot(ticker)?.currentPrice
            ?: return Result.failure(UnknownTicker(ticker))

        val gross = price * quantity
        val commission = gross * COMMISSION_RATE
        val net = gross + commission

        return mutex.withLock {
            if (cash + 1e-9 < net) {
                return@withLock Result.failure(InsufficientCash(required = net, available = cash))
            }

            cash -= net

            val current = holdingsMap[ticker]
            val newHolding = if (current == null) {
                Holding(ticker = ticker, quantity = quantity, avgBuyPrice = price)
            } else {
                val oldQty = current.quantity
                val newQty = oldQty + quantity
                val newAvg = ((current.avgBuyPrice * oldQty) + (price * quantity)) / newQty.toDouble()
                Holding(ticker = ticker, quantity = newQty, avgBuyPrice = newAvg)
            }
            holdingsMap[ticker] = newHolding

            val tx = Transaction(
                id = nextTxId++,
                timestamp = Clock.System.now(),
                type = TransactionType.BUY,
                ticker = ticker,
                quantity = quantity,
                pricePerShare = price,
                grossTotal = gross,
                commission = commission,
                netTotal = net
            )
            transactions.add(tx)

            emitPortfolioStateLocked()
            Result.success(tx)
        }
    }

    override suspend fun sell(ticker: String, quantity: Int): Result<Transaction> {
        if (quantity <= 0) return Result.failure(InvalidQuantity(quantity))

        val price = marketRepo.getSnapshot(ticker)?.currentPrice
            ?: return Result.failure(UnknownTicker(ticker))

        val gross = price * quantity
        val commission = gross * COMMISSION_RATE
        val net = gross - commission

        return mutex.withLock {
            val current = holdingsMap[ticker]
            val owned = current?.quantity ?: 0
            if (owned < quantity) {
                return@withLock Result.failure(InsufficientHoldings(ticker, quantity, owned))
            }

            val remaining = owned - quantity
            if (remaining == 0) holdingsMap.remove(ticker)
            else holdingsMap[ticker] = current!!.copy(quantity = remaining)

            cash += net

            val tx = Transaction(
                id = nextTxId++,
                timestamp = Clock.System.now(),
                type = TransactionType.SELL,
                ticker = ticker,
                quantity = quantity,
                pricePerShare = price,
                grossTotal = gross,
                commission = commission,
                netTotal = net
            )
            transactions.add(tx)

            emitPortfolioStateLocked()
            Result.success(tx)
        }
    }

    // ============================================================
    // HISTORIAL / EXPORT / SNAPSHOT
    // ============================================================

    override suspend fun getTransactions(): List<Transaction> =
        mutex.withLock { transactions.toList() }

    override suspend fun exportTransactionsCsv(): String =
        mutex.withLock {
            buildString {
                appendLine("id,timestamp,type,ticker,quantity,pricePerShare,grossTotal,commission,netTotal")
                for (t in transactions) {
                    append(t.id).append(',')
                    append(t.timestamp).append(',')
                    append(t.type).append(',')
                    append(t.ticker).append(',')
                    append(t.quantity).append(',')
                    append(String.format("%.6f", t.pricePerShare)).append(',')
                    append(String.format("%.6f", t.grossTotal)).append(',')
                    append(String.format("%.6f", t.commission)).append(',')
                    append(String.format("%.6f", t.netTotal))
                    appendLine()
                }
            }
        }

    override suspend fun getSnapshot(): PortfolioSnapshot =
        mutex.withLock { buildSnapshotLocked() }

    // ============================================================
    // Internals
    // ============================================================

    private fun emitPortfolioStateLocked() {
        val snap = buildSnapshotLocked()
        _portfolioState.value = PortfolioState(
            cash = snap.cash,
            holdings = snap.holdings,
            transactions = transactions.toList(),
            portfolioValue = snap.portfolioValue,
            pnlEuro = snap.pnlEuro,
            pnlPercent = snap.pnlPercent
        )
    }

    private fun buildSnapshotLocked(): PortfolioSnapshot {
        val holdingsList = holdingsMap.values.toList()

        val positions = holdingsList.map { h ->
            val currentPrice = marketRepo.getSnapshot(h.ticker)?.currentPrice ?: 0.0
            val invested = h.avgBuyPrice * h.quantity
            val valueNow = currentPrice * h.quantity
            val pnlEuro = valueNow - invested
            val pnlPercent = if (invested > 0.0) (pnlEuro / invested) * 100.0 else 0.0

            PositionSnapshot(
                ticker = h.ticker,
                quantity = h.quantity,
                avgBuyPrice = h.avgBuyPrice,
                currentPrice = currentPrice,
                invested = invested,
                valueNow = valueNow,
                pnlEuro = pnlEuro,
                pnlPercent = pnlPercent
            )
        }

        val totalInvested = positions.sumOf { it.invested }
        val holdingsValue = positions.sumOf { it.valueNow }
        val portfolioValue = cash + holdingsValue
        val pnlEuro = holdingsValue - totalInvested
        val pnlPercent = if (totalInvested > 0.0) (pnlEuro / totalInvested) * 100.0 else 0.0

        return PortfolioSnapshot(
            cash = cash,
            holdings = holdingsList,
            positions = positions,
            totalInvested = totalInvested,
            holdingsValue = holdingsValue,
            portfolioValue = portfolioValue,
            pnlEuro = pnlEuro,
            pnlPercent = pnlPercent
        )
    }

    /**
     * Opcional (Desktop onClose / Android onDestroy).
     * Si el scope es externo, NO lo cancelamos.
     */
    fun close() {
        pricesJob?.cancel()
        pricesJob = null
        if (externalScope == null) job.cancel()
    }
}
