package com.payments.reconciliation.service

import com.payments.payment.repository.PaymentRepository
import com.payments.reconciliation.domain.ReconciliationResult
import com.payments.reconciliation.domain.ReconciliationStatus
import com.payments.reconciliation.dto.PgCsvRow
import com.payments.reconciliation.repository.ReconciliationRepository
import com.payments.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@Service
class ReconciliationService(
    private val paymentRepository: PaymentRepository,
    private val settlementRepository: SettlementRepository,
    private val reconciliationRepository: ReconciliationRepository,
) {
    @Transactional
    fun reconcile(file: MultipartFile): List<ReconciliationResult> {
        val rows = parseCsv(file)
        return rows.map { row ->
            val payment = paymentRepository.findByPgTransactionId(row.pgTransactionId)
            val settlement = payment?.let { settlementRepository.findByOrderId(it.orderId) }

            val status = if (settlement != null && settlement.amount.compareTo(row.amount) == 0)
                ReconciliationStatus.MATCHED
            else
                ReconciliationStatus.UNMATCHED

            reconciliationRepository.save(
                ReconciliationResult(
                    pgTransactionId = row.pgTransactionId,
                    orderId = payment?.orderId ?: "UNKNOWN",
                    internalAmount = settlement?.amount ?: BigDecimal.ZERO,
                    pgAmount = row.amount,
                    status = status,
                )
            )
        }
    }

    private fun parseCsv(file: MultipartFile): List<PgCsvRow> {
        return file.inputStream.bufferedReader().useLines { lines ->
            lines.drop(1)
                .filter { it.isNotBlank() }
                .map { line ->
                    val cols = line.split(",")
                    PgCsvRow(
                        pgTransactionId = cols[0].trim(),
                        amount = BigDecimal(cols[1].trim()),
                    )
                }
                .toList()
        }
    }
}
