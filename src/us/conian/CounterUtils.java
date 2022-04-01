package us.conian;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CounterUtils {
	
public static final int CONTINUOUS = -1;
	
	private static final String SAMPLE_INTERVAL = "%sampleInterval%";
	private static final String NUM_SAMPLES = "%numSamples%";
	private static final String OUTPUT_FILE = "%outputFile%";
	
	private static final String SCRIPT = "powershell.exe \"Get-Counter -ListSet Process | Get-Counter -ErrorAction SilentlyContinue " 
			+ SAMPLE_INTERVAL + " " + NUM_SAMPLES
			+ " | select @{l=\\\"Timestamp\\\";e={([datetime]\\\"$($_.timestamp)\\\").tostring(\\\"" + CounterUtils.TIMESTAMP_PATTERN + "\\\")}},Readings,\"End\" | fl"
			+ " | Out-File -Encoding utf8 -FilePath \\\"" + OUTPUT_FILE + "\\\"\"";
	
	public static Process buildCounterProcess(int sampleInterval, int numSamples, File outputFile) throws IOException {
		if (outputFile == null)
			throw new NullPointerException();
		if (sampleInterval < 1)
			throw new IllegalArgumentException("The sample interval must be a positive value");
		String script = SCRIPT
				.replace(SAMPLE_INTERVAL, "-SampleInterval " + sampleInterval)
				.replace(NUM_SAMPLES, (numSamples < 1 ? "-Continuous" : "-MaxSamples " + numSamples))
				.replace(OUTPUT_FILE, outputFile.getAbsolutePath());
		return Runtime.getRuntime().exec(script);
	}
	
	public static final String TIMESTAMP_PATTERN = "yyyy/MM/dd HH:mm:ss";
	public static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);
	
	//group 1 = actual timestamp
	//TODO: better matching for actual timestamp
	public static final Pattern TIMESTAMP_PARSE_PATTERN = Pattern.compile("Timestamp[ ]+: ([0-9\\/: ]+)$");

	//group 1 = process name (e.g. "javaw#2"), group 2 = counter name (e.g. "working set - private")
	public static final Pattern COUNTER_PARSE_PATTERN = Pattern.compile("\\\\process\\((.+)\\)\\\\(.+) :$");
	
	public static final Pattern SAMPLE_END_PARSE_PATTERN = Pattern.compile("End[ ]+:");
	
	public static Map<String, List<Sample>> parseRaw(List<String> sampleData) throws SampleParseException {
		return parseRaw(sampleData, false);
	}
	
	//ignoreBrokenEnd = whether it is okay for the last sample set in the data to not be complete
	//(which may be the case if the counter was stopped early)
	public static Map<String, List<Sample>> parseRaw(List<String> sampleData, boolean ignoreBrokenEnd) throws SampleParseException {
		Map<String, List<Sample>> samples = new HashMap<>();
		int startLine = -1;
		int currentLine = 0;
		for (String line : sampleData) {
			if (TIMESTAMP_PARSE_PATTERN.matcher(line).matches()) {
				if (startLine != -1)
					throw new SampleParseException("Two timestamps found within the same sample set");
				else startLine = currentLine;
			}
			else if (SAMPLE_END_PARSE_PATTERN.matcher(line).find()) {
				if (startLine == -1)
					throw new SampleParseException("Two sample ends found within the same sample set");
				else {
					Map<String, Sample> set = parseSingleRaw(sampleData.subList(startLine, currentLine + 1));
					for (Map.Entry<String, Sample> entry : set.entrySet()) {
						String processName = entry.getKey();
						if (!samples.containsKey(processName))
							samples.put(processName, new ArrayList<>());
						samples.get(processName).add(entry.getValue());
					}
					startLine = -1;
				}
			}
			currentLine++;
		}
		if (!ignoreBrokenEnd && startLine != -1)
			throw new SampleParseException("The last sample is incomplete");
		return samples;
	}
	
	public static Map<String, Sample> parseSingleRaw(List<String> sampleData) throws SampleParseException {
		sampleData = new ArrayList<>(sampleData);
		sampleData.removeIf(String::isBlank);
		String timestampLine = sampleData.get(0);
		Matcher timestampMatch = TIMESTAMP_PARSE_PATTERN.matcher(timestampLine);
		if (!timestampMatch.matches())
			throw new SampleParseException("The first line does not contain a timestamp: \"" + timestampLine + "\"");
		LocalDateTime timestamp;
		try {
			timestamp = LocalDateTime.parse(timestampMatch.group(1), TIMESTAMP_FORMAT);
		} catch (DateTimeParseException e) {
			throw new SampleParseException("Failed to parse timestamp", e);
		}
		if (!SAMPLE_END_PARSE_PATTERN.matcher(sampleData.get(sampleData.size() - 1)).find())
			throw new SampleParseException("Unexpected sample end line (is the sample data incomplete?)");
		Map<String, Map<String, Number>> readings = new HashMap<>();
		//Note: the lines alternate between the name of the reading and the value,
		//with the way this is set up, odd indices are names, even indices are values
		for (int i = 2; i < sampleData.size() - 1; i+= 2) {
			String readingLine = sampleData.get(i - 1);
			Matcher readingMatch = COUNTER_PARSE_PATTERN.matcher(readingLine);
			if (!readingMatch.find()) {
				System.out.println(sampleData.get(i - 2));
				System.out.println(sampleData.get(i - 1));
				System.out.println(sampleData.get(i));
				System.out.println(sampleData.get(i + 1));
				throw new SampleParseException("Unexpected reading input on line " + i + ": \"" + readingLine + "\"");
			}
			String processName = readingMatch.group(1);
			String counterName = readingMatch.group(2);
			String valueLine = sampleData.get(i).strip();
			Number value;
			try {
				value = valueLine.contains(".")
						? Double.parseDouble(valueLine)
						: Long.parseLong(valueLine);
			} catch(NumberFormatException unused) {
				throw new SampleParseException("Unexpected value input on line " + (i + 1) + ": \"" + valueLine + "\"");
			}
			if (!readings.containsKey(processName))
				readings.put(processName, new HashMap<>());
			Map<String, Number> pData = readings.get(processName);
			if (pData.putIfAbsent(counterName, value) != null)
				throw new SampleParseException("Duplicate reading \"" + counterName + "\" found for process " + processName);
		}
		return readings.entrySet()
				.stream()
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, (entry) -> new Sample(timestamp, entry.getValue())));
	}

}
