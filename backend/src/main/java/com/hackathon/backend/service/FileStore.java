package com.hackathon.backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Receipts and payout files on the Docker volumes (CLAUDE.md §13.5). Only generated names
 * that match a safe pattern are ever used, so a path can never leave its directory.
 */
@Component
public class FileStore {

	private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,120}$");

	private final Path receiptsDir;

	private final Path exportsDir;

	public FileStore(@Value("${app.storage.receipts-dir}") String receiptsDir,
			@Value("${app.storage.exports-dir}") String exportsDir) {
		this.receiptsDir = Path.of(receiptsDir).toAbsolutePath().normalize();
		this.exportsDir = Path.of(exportsDir).toAbsolutePath().normalize();
	}

	public void writeReceipt(String name, byte[] bytes) {
		write(receiptsDir, name, bytes);
	}

	public byte[] readReceipt(String name) {
		return read(receiptsDir, name);
	}

	public void deleteReceipt(String name) {
		delete(receiptsDir, name);
	}

	public void writeExport(String name, byte[] bytes) {
		write(exportsDir, name, bytes);
	}

	public byte[] readExport(String name) {
		return read(exportsDir, name);
	}

	public void deleteExport(String name) {
		delete(exportsDir, name);
	}

	private static Path resolve(Path dir, String name) {
		if (name == null || !SAFE_NAME.matcher(name).matches() || name.contains("..")) {
			throw new IllegalArgumentException("Unsafe file name");
		}
		return dir.resolve(name);
	}

	private static void write(Path dir, String name, byte[] bytes) {
		try {
			Files.createDirectories(dir);
			Files.write(resolve(dir, name), bytes, StandardOpenOption.CREATE_NEW);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static byte[] read(Path dir, String name) {
		try {
			return Files.readAllBytes(resolve(dir, name));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void delete(Path dir, String name) {
		try {
			Files.deleteIfExists(resolve(dir, name));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
