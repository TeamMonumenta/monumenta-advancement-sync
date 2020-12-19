package com.playmonumenta.advancementsync.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

public class FileUtils {
	public static String readFile(String fileName) throws Exception, FileNotFoundException {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		File file;

		if (fileName == null || fileName.isEmpty()) {
			throw new Exception("Filename is null or empty");
		}

		file = new File(fileName);
		if (!file.exists()) {
			throw new FileNotFoundException("File '" + fileName + "' does not exist");
		}

		InputStreamReader reader = null;
		final int bufferSize = 1024;
		final char[] buffer = new char[bufferSize];
		final StringBuilder content = new StringBuilder();

		try {
			reader = new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8);
			while (true) {
				int rsz = reader.read(buffer, 0, buffer.length);
				if (rsz < 0) {
					break;
				}
				content.append(buffer, 0, rsz);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		return content.toString();
	}

	public static void writeFile(String fileName, String contents) throws IOException {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		File file = new File(fileName);

		if (!file.exists()) {
			file.getParentFile().mkdirs();
			file.createNewFile();
		}

		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8);
			writer.write(contents);
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static JsonObject readJson(String fileName) throws Exception, FileNotFoundException {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		File file;

		if (fileName == null || fileName.isEmpty()) {
			throw new Exception("Filename is null or empty");
		}

		file = new File(fileName);
		if (!file.exists()) {
			throw new FileNotFoundException("File '" + fileName + "' does not exist");
		}

		InputStreamReader reader = null;
		Gson gson = new Gson();
		JsonObject object = null;

		try {
			reader = new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8);
			object = gson.fromJson(reader, JsonObject.class);
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		return object;
	}

	public static void writeJson(String fileName, JsonObject json) throws IOException {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		File file = new File(fileName);

		if (!file.exists()) {
			file.getParentFile().mkdirs();
			file.createNewFile();
		}

		OutputStreamWriter writer = null;
		JsonWriter jsonWriter = null;
		Gson gson;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8);
			gson = new Gson();
			jsonWriter = gson.newJsonWriter(writer);
			jsonWriter.setIndent("    ");
			gson.toJson(json, jsonWriter);
		} finally {
			if (jsonWriter != null) {
				jsonWriter.close();
			}
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static String readZipFile(String zipFileName, String entryName) throws Exception {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		ZipFile zip;

		if (zipFileName == null || zipFileName.isEmpty()) {
			throw new Exception("Zip filename is null or empty");
		}

		if (entryName == null || entryName.isEmpty()) {
			throw new Exception("Entryname is null or empty");
		}

		zip = new ZipFile(zipFileName);
		if (zip == null) {
			throw new Exception("ZipFile '" + zipFileName + "' could not be read");
		}

		ZipEntry entry = zip.getEntry(entryName);
		if (entry == null) {
			throw new Exception("ZipEntry '" + entryName + "' not found");
		}

		InputStreamReader reader = null;
		final int bufferSize = 1024;
		final char[] buffer = new char[bufferSize];
		final StringBuilder content = new StringBuilder();

		try {
			reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8);
			while (true) {
				int rsz = reader.read(buffer, 0, buffer.length);
				if (rsz < 0) {
					break;
				}
				content.append(buffer, 0, rsz);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		return content.toString();
	}
}
