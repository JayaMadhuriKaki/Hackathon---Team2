package com.hackathon.backend.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.hackathon.backend.domain.Ids;

/**
 * Synthetic organisation seeded automatically on {@code docker compose up} (DATA-001, DATA-002,
 * DATA-004, DATA-005; database/SPEC.md §7.1): 8 departments, 150 employees with a reporting
 * hierarchy, synthetic bank accounts and IFSC codes, and the demo logins.
 * <ul>
 * <li>Deterministic: fixed seed {@value #SEED}, so every clone gets identical data.</li>
 * <li>Idempotent: skipped when 150 users exist; rows that already exist are left as they are.</li>
 * <li>Runs with the owner credentials, because the app role cannot write to {@code users}.</li>
 * <li>Only the 8 demo logins get {@code SEED_DEMO_PASSWORD}; every other user gets an unusable random
 * password.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

	static final long SEED = 20261127L;

	static final int TOTAL_USERS = 150;

	private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

	private static final String[] FIRST = { "Aarav", "Aditi", "Akash", "Ananya", "Arjun", "Bhavna", "Chetan", "Deepa",
			"Dev", "Divya", "Farhan", "Gauri", "Harsh", "Isha", "Jatin", "Kavya", "Kiran", "Lakshmi", "Manish", "Meera",
			"Nikhil", "Nisha", "Om", "Pooja", "Pranav", "Priya", "Rahul", "Rekha", "Rohan", "Sana", "Siddharth", "Sneha",
			"Tanvi", "Tarun", "Uma", "Varun", "Vidya", "Yash", "Zoya", "Irfan" };

	private static final String[] LAST = { "Acharya", "Bose", "Chatterjee", "Desai", "Gill", "Hegde", "Iyer", "Joshi",
			"Kapoor", "Khan", "Krishnan", "Mehta", "Menon", "Mishra", "Nair", "Patel", "Pillai", "Rao", "Reddy", "Saxena",
			"Sen", "Shah", "Sharma", "Singh", "Thomas", "Verma" };

	private static final String[] IFSC_PREFIX = { "XFLB0", "SYNB0", "TSTB0" };

	/** Departments in database/SPEC.md §7.1 order with head-count; Finance is built separately. */
	private static final String[][] LINE_DEPARTMENTS = { { "Consulting", "19" }, { "Engineering", "19" },
			{ "Sales", "19" }, { "Marketing", "19" }, { "Operations", "19" }, { "Human Resources", "18" },
			{ "IT Support", "18" } };

	/** Heads of these departments have no manager (CL-09 demo cases). */
	private static final Set<String> NO_MANAGER_HEADS = Set.of("Operations", "IT Support");

	private final JdbcTemplate owner;

	private final TransactionTemplate tx;

	private final PasswordEncoder passwords;

	private final String password;

	public DemoUserSeeder(@Value("${spring.datasource.url}") String url,
			@Value("${spring.flyway.user}") String ownerUser, @Value("${spring.flyway.password}") String ownerPassword,
			@Value("${app.seed.demo-password:}") String password, PasswordEncoder passwords) {
		DriverManagerDataSource ds = new DriverManagerDataSource(url, ownerUser, ownerPassword);
		this.owner = new JdbcTemplate(ds);
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
		this.passwords = passwords;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer existing = owner.queryForObject("select count(*) from users", Integer.class);
		if (existing != null && existing >= TOTAL_USERS) {
			log.info("Synthetic data already present ({} users); seed skipped", existing);
			return;
		}
		if (password == null || password.length() < 8) {
			throw new IllegalStateException("SEED_DEMO_PASSWORD must be set (at least 8 characters) to seed demo users");
		}
		List<Person> plan = plan();
		String demoHash = passwords.encode(password);
		String lockedHash = passwords.encode(UUID.randomUUID().toString());
		int inserted = tx.execute(status -> insert(plan, demoHash, lockedHash));
		Integer total = owner.queryForObject("select count(*) from users", Integer.class);
		log.info("Synthetic data seeded: {} new users, {} users in {} departments", inserted, total,
				owner.queryForObject("select count(*) from departments", Integer.class));
	}

	/** One employee of the synthetic organisation; {@code managerCode} refers to an earlier row. */
	record Person(String code, String name, String email, String role, String managerCode, String department,
			boolean demoLogin) {
	}

	/**
	 * The full organisation, managers before their reports (database/SPEC.md §7.1). EMP0001..EMP0008
	 * are the demo logins in the same codes as earlier installs.
	 */
	static List<Person> plan() {
		Random random = new Random(SEED);
		Set<String> usedEmails = new HashSet<>();
		List<Person> people = new ArrayList<>();
		int[] next = { 9 };

		// Fixed demo personas.
		people.add(new Person("EMP0001", "Farah Iyer", "financehead@expenseflow.test", "FINANCE_HEAD", null, "Finance",
				true));
		people.add(new Person("EMP0002", "Arjun Mehta", "auditor@expenseflow.test", "FINANCE_AUDITOR", "EMP0001",
				"Finance", true));
		people.add(new Person("EMP0003", "Bina Das", "auditor2@expenseflow.test", "FINANCE_AUDITOR", "EMP0001",
				"Finance", true));
		String consultingHead = code(next);
		people.add(person(random, usedEmails, consultingHead, "EMPLOYEE", "EMP0001", "Consulting"));
		people.add(new Person("EMP0004", "Manoj Pillai", "manager@expenseflow.test", "EMPLOYEE", consultingHead,
				"Consulting", true));
		people.add(new Person("EMP0005", "Ananya Rao", "employee@expenseflow.test", "EMPLOYEE", "EMP0004",
				"Consulting", true));
		people.add(new Person("EMP0006", "Ravi Kumar", "employee2@expenseflow.test", "EMPLOYEE", "EMP0004",
				"Consulting", true));
		people.add(new Person("EMP0007", "Neha Kapoor", "nomanager@expenseflow.test", "EMPLOYEE", null, "Operations",
				true));
		people.add(new Person("EMP0008", "Sam Thomas", "staff@expenseflow.test", "EMPLOYEE", "EMP0002", "Finance",
				true));

		// Finance (19): head, 4 auditors (2 are demo), 14 staff spread over the auditors (1 is demo).
		List<String> auditors = new ArrayList<>(List.of("EMP0002", "EMP0003"));
		for (int i = 0; i < 2; i++) {
			String c = code(next);
			people.add(person(random, usedEmails, c, "FINANCE_AUDITOR", "EMP0001", "Finance"));
			auditors.add(c);
		}
		for (int i = 0; i < 13; i++) {
			people.add(person(random, usedEmails, code(next), "EMPLOYEE", auditors.get(i % 4), "Finance"));
		}

		// Line departments: head, 2 team leads, the rest split across the leads.
		for (String[] dept : LINE_DEPARTMENTS) {
			String name = dept[0];
			int size = Integer.parseInt(dept[1]);
			String head;
			List<String> leads = new ArrayList<>();
			int alreadyPlaced;
			if (name.equals("Consulting")) {
				head = consultingHead;
				leads.add("EMP0004");
				alreadyPlaced = 4; // head, manager, employee, employee2
			}
			else if (name.equals("Operations")) {
				head = "EMP0007";
				alreadyPlaced = 1;
			}
			else {
				head = code(next);
				people.add(person(random, usedEmails, head, "EMPLOYEE",
						NO_MANAGER_HEADS.contains(name) ? null : "EMP0001", name));
				alreadyPlaced = 1;
			}
			while (leads.size() < 2) {
				String lead = code(next);
				people.add(person(random, usedEmails, lead, "EMPLOYEE", head, name));
				leads.add(lead);
				alreadyPlaced++;
			}
			for (int i = 0; alreadyPlaced < size; i++, alreadyPlaced++) {
				people.add(person(random, usedEmails, code(next), "EMPLOYEE", leads.get(i % 2), name));
			}
		}
		if (people.size() != TOTAL_USERS) {
			throw new IllegalStateException("Seed plan has " + people.size() + " users, expected " + TOTAL_USERS);
		}
		return people;
	}

	private static String code(int[] next) {
		return String.format("EMP%04d", next[0]++);
	}

	private static Person person(Random random, Set<String> usedEmails, String code, String role, String managerCode,
			String department) {
		String first = FIRST[random.nextInt(FIRST.length)];
		String last = LAST[random.nextInt(LAST.length)];
		String email = (first + "." + last + "." + code.substring(3) + "@expenseflow.test").toLowerCase();
		usedEmails.add(email);
		return new Person(code, first + " " + last, email, role, managerCode, department, false);
	}

	private int insert(List<Person> plan, String demoHash, String lockedHash) {
		int inserted = 0;
		for (Person p : plan) {
			UUID deptId = department(p.department());
			UUID managerId = p.managerCode() == null ? null
					: owner.queryForObject("select id from users where employee_code = ?", UUID.class, p.managerCode());
			int n = Integer.parseInt(p.code().substring(3));
			inserted += owner.update("""
					insert into users (id, employee_code, full_name, email, password_hash, role, manager_id, department_id,
					bank_account_number, ifsc_code) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing""",
					Ids.newId(), p.code(), p.name(), p.email(), p.demoLogin() ? demoHash : lockedHash, p.role(),
					managerId, deptId, String.format("9%011d", n),
					IFSC_PREFIX[n % IFSC_PREFIX.length] + String.format("%06d", 100000 + n * 37 % 900000));
		}
		return inserted;
	}

	private UUID department(String name) {
		owner.update("insert into departments (id, name) values (?, ?) on conflict (name) do nothing", Ids.newId(), name);
		return owner.queryForObject("select id from departments where name = ?", UUID.class, name);
	}

}
