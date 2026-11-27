package com.hackathon.backend.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.hackathon.backend.service.support.Money;

/**
 * The mock NEFT bank file of CLAUDE.md §2.8: UTF-8, comma, header row, every text field
 * quoted (inner quotes doubled), amounts with two decimals and no separators, ISO dates, one
 * row per claim in claim-number order. Values starting with =, +, -, @, tab or CR get a
 * leading apostrophe so a spreadsheet never runs them as formulas (CLAUDE.md §13.4).
 */
public final class PayoutCsvWriter {

	public static final List<String> HEADER = List.of("batch_number", "payment_reference", "value_date",
			"payment_mode", "employee_id", "beneficiary_name", "beneficiary_account_number", "beneficiary_ifsc",
			"amount", "currency", "claim_id", "department", "narration");

	static final int NARRATION_MAX = 30;

	private PayoutCsvWriter() {
	}

	public static String fileName(String batchNumber, LocalDate exportDate) {
		return "EXPENSEFLOW_PAYOUT_" + batchNumber + "_" + exportDate.toString().replace("-", "") + ".csv";
	}

	public static byte[] write(String batchNumber, LocalDate valueDate, List<Row> rows) {
		StringBuilder out = new StringBuilder();
		out.append(String.join(",", HEADER.stream().map(PayoutCsvWriter::quote).toList())).append("\r\n");
		int n = 0;
		for (Row r : rows) {
			n++;
			String narration = "Expense reimbursement " + r.claimNumber();
			if (narration.length() > NARRATION_MAX) {
				narration = narration.substring(0, NARRATION_MAX).trim();
			}
			List<String> cells = List.of(quote(batchNumber), quote(batchNumber + "-" + String.format("%04d", n)),
					quote(valueDate.toString()), quote("NEFT"), quote(r.employeeCode()), quote(r.beneficiaryName()),
					quote(r.accountNumber()), quote(r.ifsc()), Money.format(r.amount()), quote("INR"),
					quote(r.claimNumber()), quote(r.department()), quote(narration));
			out.append(String.join(",", cells)).append("\r\n");
		}
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	static String neutralise(String value) {
		if (value == null) {
			return "";
		}
		if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
			return "'" + value;
		}
		return value;
	}

	static String quote(String value) {
		return "\"" + neutralise(value).replace("\"", "\"\"") + "\"";
	}

	public record Row(String claimNumber, String employeeCode, String beneficiaryName, String accountNumber,
			String ifsc, BigDecimal amount, String department) {
	}

}
