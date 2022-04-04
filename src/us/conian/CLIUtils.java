package us.conian;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CLIUtils {
	
	public static final File DATA_FOLDER;
	
	static {
		try {
			DATA_FOLDER = new File(new File(CLIUtils.class.getProtectionDomain().getCodeSource().getLocation()
					.toURI().getPath()).getParent(), "data");
			if (!DATA_FOLDER.exists())
				DATA_FOLDER.mkdirs();
		} catch(Exception e) {
			throw new InternalError("Failed to find or create cluster directory: " + e.getMessage());
		}
	}
	
	public static final DateTimeFormatter SAMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	
	public static int determineSampleInterval(BufferedReader in, String arg) throws IOException {
		int sampleInterval = -1;
		if (arg != null)
			try {
				sampleInterval = Integer.parseInt(arg);
				if (sampleInterval < 1)
					System.err.println("Invalid sample interval argument (cannot be less than 1): \"" + arg + "\"");
			} catch (NumberFormatException unused) {
				System.err.println("Invalid sample interval argument (must be an integer): \"" + arg + "\"");
			}
		while (sampleInterval < 1) {
			System.out.print("Please specify the sample interval (the minimum time in seconds between each sample) > ");
			while (!in.ready()) { }
			String input = in.readLine();
			try {
				sampleInterval = Integer.parseInt(input);
				if (sampleInterval < 1)
					System.err.println("The sample interval must be a positive integer");
			} catch (NumberFormatException e1) {
				System.err.println("Uh-oh, it looks like you didn't enter an integer: \'"
						+ input + "\'");
			}
		}
		System.out.println("Using a sample interval of " + sampleInterval);
		return sampleInterval;
	}
	
	public static int determineNumSamples(BufferedReader in, String arg) throws IOException {
		int numSamples = 0;
		if (arg != null)
			try {
				numSamples = Integer.parseInt(arg);
				if (numSamples == 0)
					System.err.println("Invalid sample number argument (cannot be 0): \"" + arg + "\"");
			} catch (NumberFormatException unused) {
				System.err.println("Invalid sample number argument (must be an integer): \"" + arg + "\"");
			}
		while (numSamples == 0) {
			System.out.print("Please specify the number of samples to run (or a negative number to run samples continuously) > ");
			while (!in.ready()) { }
			String input = in.readLine();
			try {
				numSamples = Integer.parseInt(input);
				if (numSamples == 0)
					System.err.println("The number of samples cannot be 0");
			} catch (NumberFormatException e1) {
				System.err.println("Uh-oh, it looks like you didn't enter an integer: \'"
						+ input + "\'");
			}
		}
		System.out.println((numSamples < 1 ? "Gathering samples continuously" : "Gathering " + numSamples + " samples"));
		return numSamples;
	}
	
	public static boolean determineShouldSave(BufferedReader in) throws IOException {
		while (true) {
			System.out.print("Do you want to save the collected samples? (Y/n) > ");
			while (!in.ready()) { }
			String input = in.readLine();
			if (input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes"))
				return true;
			else if (input.equalsIgnoreCase("n") || input.equalsIgnoreCase("no"))
				return false;
			else System.err.println("Invalid response. Please answer with \"y\", \"yes\", \"n\", or \"no\"");
		}
	}

	public static File createSampleDirectory(BufferedReader in, File parentDirectory, LocalDateTime time) throws IOException {
		if (parentDirectory == null)
			throw new NullPointerException();
		if (!parentDirectory.isDirectory())
			throw new IllegalArgumentException("Given parent directory is not a directory: " + parentDirectory.getAbsolutePath());
		File sampleFolder = new File(parentDirectory, SAMPLE_DATE_FORMAT.format(time));
		while (sampleFolder.exists() && sampleFolder.listFiles().length != 0) {
			System.err.println("The directory \"" + sampleFolder.getAbsolutePath() + "\" already exists and is not empty");
			System.out.print("Please enter a new directory name instead > ");
			while (!in.ready()) { }
			sampleFolder = new File(parentDirectory, in.readLine());
		}
		sampleFolder.mkdirs();
		return sampleFolder;
	}
	
	public static File createSampleFile(File directory, String counterName) throws IOException {
		File sampleFile = new File(directory, counterName + CSVUtils.FILE_EXTENSION);
		if (sampleFile.exists()) {
			System.err.println("The file \"" + sampleFile.getAbsolutePath() + "\" already exists in this sample set, skipping");
			return null;
		}
		sampleFile.createNewFile();
		return sampleFile;
	}

}
