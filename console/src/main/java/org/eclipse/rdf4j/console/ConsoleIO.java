/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.console;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.rdf4j.rio.RDFParseException;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * @author Dale Visser
 */
class ConsoleIO {

	private static final String PLEASE_OPEN_FIRST = "please open a repository first";

	private final Terminal terminal;
	private final LineReader input;
	private final ConsoleState appInfo;

	private boolean echo = false;
	private boolean quiet = false;
	private boolean force = false;
	private boolean cautious = false;
	private boolean errorWritten;

	/**
	 * Constructor
	 * 
	 * @param input
	 * @param out
	 * @param info
	 * @throws IOException 
	 */
	ConsoleIO(InputStream input, OutputStream out, ConsoleState info) throws IOException {
		this.terminal = TerminalBuilder.builder().system(false).streams(input, out).build();
		this.input = LineReaderBuilder.builder().terminal(terminal).build();
		this.appInfo = info;
	}

	/**
	 * Constructor
	 * 
	 * @param info
	 * @throws IOException 
	 */
	ConsoleIO(ConsoleState info) throws IOException {
		this.terminal = TerminalBuilder.terminal();
		this.input = LineReaderBuilder.builder().terminal(terminal).build();
		this.appInfo = info;
	}

	/**
	 * Read a command from input
	 * 
	 * @return one line of input, or null on error
	 * @throws IOException 
	 */
	protected String readCommand() throws IOException {
		try {
			String line = input.readLine(getPrompt());
			if (line == null) {
				return null;
			}
			line = line.trim();
			if (line.endsWith(".")) {
				line = line.substring(0, line.length() - 1);
			}
			return line;
		} catch (EndOfFileException e) {
			return null;
		}
	}

	/**
	 * Get command prompt.
	 * 
	 * Contains the name of the current repository when connected.
	 * 
	 * @return command prompt string
	 */
	private String getPrompt() {
		String repositoryID = appInfo.getRepositoryID();
		if (quiet) {
			return "";
		} else if (repositoryID != null) {
			return repositoryID + "> ";
		} else {
			return "> ";
		}
	}

	/**
	 * Reads multiple lines from the input until a line that with a '.' on its own is read.
	 * 
	 * @throws IOException
	 */
	protected String readMultiLineInput() throws IOException {
		return readMultiLineInput("> ");
	}

	/**
	 * Reads multiple lines from the input until a line that with a '.' on its own is read.
	 * 
	 * @throws IOException
	 */
	protected String readMultiLineInput(String prompt) throws IOException {
		String line = input.readLine(prompt);
		String result = null;
		
		if (line != null) {
			final StringBuilder buf = new StringBuilder(256);
			buf.append(line);
			
			while (line != null && !(line.length() == 1 && line.endsWith("."))) {
				line = input.readLine("> ");
				buf.append('\n');
				buf.append(line);
			}
			// Remove closing dot
			buf.setLength(buf.length() - 1);
			result = buf.toString().trim();
		}
		if (echo) {
			writeln(result);
		}
		return result;
	}

	/**
	 * Read message from input 
	 * 
	 * @param message one or multiple messages
	 * @return
	 * @throws IOException 
	 */
	protected String readln(String... message) throws IOException {
		String prompt = !quiet && message.length > 0 && message[0] != null ? message[0] : "";
		String result = input.readLine(prompt);
		
		if (echo) {
			writeln(result);
		}
		return result;
	}

	/**
	 * Read password from input
	 * 
	 * @param prompt prompt to display
	 * @return password string
	 * @throws IOException 
	 */
	protected String readPassword(final String prompt) throws IOException {
		String result = input.readLine(prompt, '*');
		if (echo && !result.isEmpty()) {
			writeln("************");
		}
		return result;
	}

	/**
	 * Write a string
	 * 
	 * @param string string to write
	 */
	protected void write(final String string) {
		terminal.writer().print(string);
	}

	/**
	 * Write a newline
	 */
	protected void writeln() {
		terminal.writer().println();
	}

	/**
	 * Write a string, followed by a newline
	 * 
	 * @param string string to write
	 */
	protected void writeln(final String string) {
		terminal.writer().println(string);
	}

	/**
	 * Write an error message
	 * 
	 * @param errMsg error message
	 */
	protected void writeError(final String errMsg) {
		terminal.writer().println(errMsg);
		errorWritten = true;
	}

	/**
	 * Write a "please open first" error message
	 */
	protected void writeUnopenedError() {
		writeError(PLEASE_OPEN_FIRST);
	}

	/**
	 * Write parser error
	 * 
	 * @param prefix
	 * @param lineNo line number
	 * @param colNo column number
	 * @param msg message to write
	 */
	protected void writeParseError(final String prefix, final long lineNo, final long colNo,
			final String msg) {
		String locationString = RDFParseException.getLocationString(lineNo, colNo);
		int locSize = locationString.length();
		
		final StringBuilder builder = new StringBuilder(locSize + prefix.length() + msg.length() + 3);
		builder.append(prefix).append(": ").append(msg);
		if (locSize > 0) {
			builder.append(" ").append(locationString);
		}
		writeError(builder.toString());
	}

	/**
	 * Ask if the user wants to continue
	 * 
	 * @param msg confirmation question
	 * @param defaultValue true when default is yes
	 * @return true when continue
	 * @throws IOException 
	 */
	protected boolean askProceed(final String msg, final boolean defaultValue) throws IOException {
		final String defaultString = defaultValue ? "yes" : "no";
		boolean result = force ? true : (cautious ? false : defaultValue);
		
		if (!force && !cautious) {
			while (true) {
				writeln(msg);
				final String reply = readln("Proceed? (yes|no) [" + defaultString + "]: ");
				if ("no".equalsIgnoreCase(reply) || "no.".equalsIgnoreCase(reply)) {
					result = false;
					break;
				} else if ("yes".equalsIgnoreCase(reply) || "yes.".equalsIgnoreCase(reply)) {
					result = true;
					break;
				} else if (reply.trim().isEmpty()) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Whether to echo user input to output stream
	 * 
	 * @param echo true to echo input
	 */
	protected void setEcho(boolean echo) {
		this.echo = echo;
	}

	/**
	 * Whether to suppress printing of prompts to output
	 * 
	 * @param quiet true to surpress printing
	 */
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}

	/**
	 * Force commands to proceed
	 */
	public void setForce() {
		this.force = true;
	}

	/**
	 * Be cautious when executing commands, opposite of force
	 */
	public void setCautious() {
		this.cautious = true;
	}

	/**
	 * Check if an error was written to the console
	 * 
	 * @return true when error wwas written
	 */
	public boolean wasErrorWritten() {
		return errorWritten;
	}
}