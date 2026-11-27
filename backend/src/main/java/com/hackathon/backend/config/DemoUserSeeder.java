package com.hackathon.backend.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hackathon.backend.domain.Ids;

/**
 * Minimal synthetic demo logins (DATA-004, database/SPEC.md §7.1 personas) so the stack is usable
 * straight after {@code docker compose up}. Runs once (skips if EMP0001 exists), with the owner
 * credentials because the app role cannot write to {@code users}. The full 150-user / 3,000-claim
 * seed (DATA-001..003) is the Python seed script's job.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

	private final JdbcTemplate owner;

	private final PasswordEncoder passwords;

	private final String password;

	public DemoUserSeeder(@Value("${spring.datasource.url}") String url,
			@Value("${spring.flyway.user}") String ownerUser, @Value("${spring.flyway.password}") String ownerPassword,
			@Value("${app.seed.demo-password:}") String password, PasswordEncoder passwords) {
		this.owner = new JdbcTemplate(new DriverManagerDataSource(url, ownerUser, ownerPassword));
		this.passwords = passwords;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer existing = owner.queryForObject("select count(*) from users where employee_code = 'EMP0001'",
				Integer.class);
		if (existing != null && existing > 0) {
			log.info("Demo users already present; seed skipped");
			return;
		}
		if (password == null || password.length() < 8) {
			throw new IllegalStateException("SEED_DEMO_PASSWORD must be set (at least 8 characters) to seed demo users");
		}
		String hash = passwords.encode(password);
		UUID finance = dept("Finance");
		UUID consulting = dept("Consulting");
		UUID operations = dept("Operations");
		UUID head = user(1, "Farah Iyer", "financehead", "FINANCE_HEAD", null, finance, hash);
		UUID auditor = user(2, "Arjun Mehta", "auditor", "FINANCE_AUDITOR", head, finance, hash);
		user(3, "Bina Das", "auditor2", "FINANCE_AUDITOR", head, finance, hash);
		UUID manager = user(4, "Manoj Pillai", "manager", "EMPLOYEE", head, consulting, hash);
		user(5, "Ananya Rao", "employee", "EMPLOYEE", manager, consulting, hash);
		user(6, "Ravi Kumar", "employee2", "EMPLOYEE", manager, consulting, hash);
		user(7, "Neha Kapoor", "nomanager", "EMPLOYEE", null, operations, hash);
		user(8, "Sam Thomas", "staff", "EMPLOYEE", auditor, finance, hash);
		log.info("Seeded 8 synthetic demo users (<role>@expenseflow.test)");
	}

	private UUID dept(String name) {
		UUID id = Ids.newId();
		owner.update("insert into departments (id, name) values (?, ?) on conflict (name) do nothing", id, name);
		return owner.queryForObject("select id from departments where name = ?", UUID.class, name);
	}

	private UUID user(int n, String name, String login, String role, UUID managerId, UUID deptId, String hash) {
		UUID id = Ids.newId();
		owner.update("""
				insert into users (id, employee_code, full_name, email, password_hash, role, manager_id, department_id,
				bank_account_number, ifsc_code) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id,
				String.format("EMP%04d", n), name, login + "@expenseflow.test", hash, role, managerId, deptId,
				String.format("9%011d", n), String.format("XFLB0%06d", n));
		return id;
	}

}
