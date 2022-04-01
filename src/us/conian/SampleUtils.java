package us.conian;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

public class SampleUtils {
	
	public static final String TIMESTAMP_PATTERN = "yyyy/MM/dd HH:mm:ss";
	public static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);
	
	public static Sample[] parseRaw(List<String> sampleData) throws SampleParseException {
		sampleData = new ArrayList<>(sampleData);
		sampleData.removeIf(String::isBlank);
		if (sampleData.size() == 0)
			return new Sample[0];
		List<Sample> samples = new ArrayList<>();
		//The number of samples we expect to parse based on the number of timestamps we found.
		//This is just a failsafe to help ensure the data is being parsed correctly
		int expectedSampleCount = 1;
		int start = 0;
		//starting at index 1 because we're assuming the first line is the first timestamp,
		//if it's not it'll be caught while being parsed
		for (int i = 1; i < sampleData.size(); i++) {
			String line = sampleData.get(i);
			if (CounterUtils.TIMESTAMP_PARSE_PATTERN.matcher(line).matches()) {
				samples.add(parseSingleRaw(sampleData.subList(start, i), start + 1));
				start = i;
				expectedSampleCount++;
			}
		}
		//The last sample in the data isn't parsed by the loop because there is no next timestamp,
		//so we need to add it manually (this also ensures data with only a single sample gets parsed
		samples.add(parseSingleRaw(sampleData.subList(start, sampleData.size()), start + 1));
		if (samples.size() != expectedSampleCount)
			throw new SampleParseException("Unexpected number of samples parsed. Expected "
					+ expectedSampleCount + ", received " + samples.size());
		return samples.toArray(Sample[]::new);
	}
	
	public static Sample parseSingleRaw(List<String> sampleData) throws SampleParseException {
		return parseSingleRaw(sampleData, 1);
	}
	
	//startingLine is just the starting line number for this sample, for better debugging messages
	private static Sample parseSingleRaw(List<String> sampleData, int startingLine) throws SampleParseException {
		//must have at least 3 lines, the first being the timestamp,
		//and the next 2 being a reading and value
		if (sampleData.size() < 3) {
			throw new SampleParseException("Not enough lines for a full sample");
		}
		//the number of lines should be odd, since there's 1 timestamp line
		//and 2 lines per reading
		if (sampleData.size() % 2 == 0)
			throw new SampleParseException("Unexpected number of lines");
		String timestampLine = sampleData.get(0);
		Matcher timestampMatch = CounterUtils.TIMESTAMP_PARSE_PATTERN.matcher(timestampLine);
		if (!timestampMatch.matches())
			throw new SampleParseException("The first line does not contain a timestamp: \"" + timestampLine + "\"");
		LocalDateTime timestamp;
		try {
			timestamp = LocalDateTime.parse(timestampMatch.group(1), TIMESTAMP_FORMAT);
		} catch (DateTimeParseException e) {
			throw new SampleParseException("Failed to parse timestamp", e);
		}
		Map<String, Number> readings = new HashMap<>();
		//Note: the lines alternate between the name of the reading and the value,
		//with the way this is set up, odd indices are names, even indices are values
		for (int i = 2; i < sampleData.size(); i+= 2) {
			String readingLine = sampleData.get(i - 1);
			Matcher readingMatch = CounterUtils.COUNTER_PARSE_PATTERN.matcher(readingLine);
			if (!readingMatch.find())
				throw new SampleParseException("Unexpected reading input on line " + (i + startingLine) + ": \"" + readingLine + "\"");
			String reading = readingMatch.group(2);
			String valueLine = sampleData.get(i).strip();
			Number value;
			try {
				value = valueLine.contains(".")
						? Double.parseDouble(valueLine)
						: Long.parseLong(valueLine);
			} catch(NumberFormatException unused) {
				throw new SampleParseException("Unexpected value input on line " + (i + 1 + startingLine) + ": \"" + valueLine + "\"");
			}
			if (readings.putIfAbsent(reading, value) != null)
				throw new SampleParseException("Duplicate reading found: \"" + reading + "\"");
		}
		return new Sample(timestamp, readings);
	}
	
	private static DecimalFormat DOUBLE_FORMAT = new DecimalFormat("#");
	
	static {
		DOUBLE_FORMAT.setMaximumFractionDigits(8);
	}
	
	private static DecimalFormat LONG_FORMAT = new DecimalFormat("#");
	
	static {
		LONG_FORMAT.setMaximumFractionDigits(0);
	}
	
	public static String toCSVString(Sample sample) {
		if (sample == null)
			throw new NullPointerException();
		return new StringBuilder()
				.append(TIMESTAMP_FORMAT.format(sample.timestamp()))
				.append("\n")
				.append(CSVUtils.toCSVString(sample.readings(), (s) -> s, (v) -> {
					if (v instanceof Double d)
						return DOUBLE_FORMAT.format(d);
					else if (v instanceof Long l)
						return LONG_FORMAT.format(l);
					else return v.toString();
				}))
				.append("\n")
				.toString();
	}
	
	//TODO: Test
	public static Sample[] fromCSVString(String csv) throws SampleParseException {
		List<String> sampleData = csv.lines().toList();
		sampleData.removeIf(String::isBlank);
		if (sampleData.size() == 0)
			return new Sample[0];
		List<Sample> samples = new ArrayList<>();
		//The number of samples we expect to parse based on the number of timestamps we found.
		//This is just a failsafe to help ensure the data is being parsed correctly
		int expectedSampleCount = 1;
		int start = 0;
		//starting at index 1 because we're assuming the first line is the first timestamp,
		//if it's not it'll be caught while being parsed
		for (int i = 1; i < sampleData.size(); i++) {
			String line = sampleData.get(i);
			if (CounterUtils.TIMESTAMP_PARSE_PATTERN.matcher(line).matches()) {
				samples.add(singleFromCSVString(sampleData.subList(start, i)));
				start = i;
				expectedSampleCount++;
			}
		}
		//The last sample in the data isn't parsed by the loop because there is no next timestamp,
		//so we need to add it manually (this also ensures data with only a single sample gets parsed
		samples.add(singleFromCSVString(sampleData.subList(start, sampleData.size())));
		if (samples.size() != expectedSampleCount)
			throw new SampleParseException("Unexpected number of samples parsed. Expected "
					+ expectedSampleCount + ", received " + samples.size());
		return samples.toArray(Sample[]::new);
	}
	
	private static Sample singleFromCSVString(List<String> sampleData) throws SampleParseException {
		//must have at least 2 lines, the first being the timestamp,
		//and the next being a reading and value entry
		if (sampleData.size() < 2) {
			throw new SampleParseException("Not enough lines for a full sample");
		}
		String timestampLine = sampleData.get(0);
		Matcher timestampMatch = CounterUtils.TIMESTAMP_PARSE_PATTERN.matcher(timestampLine);
		if (!timestampMatch.matches())
			throw new SampleParseException("The first line does not contain a timestamp: \"" + timestampLine + "\"");
		LocalDateTime timestamp;
		try {
			timestamp = LocalDateTime.parse(timestampMatch.group(1), TIMESTAMP_FORMAT);
		} catch (DateTimeParseException e) {
			throw new SampleParseException("Failed to parse timestamp", e);
		}
		Map<String, Number> readings = CSVUtils.fromCSVEntries(
				sampleData.subList(1, sampleData.size()),
				(s) -> s,
				(valueLine) -> {
					try {
						return valueLine.contains(".")
								? Double.parseDouble(valueLine)
								: Long.parseLong(valueLine);
					} catch(NumberFormatException unused) {
						throw new SampleParseException("Unexpected value input: \"" + valueLine + "\"");
					}
				}
				);
		return new Sample(timestamp, readings);
	}
	
	public static Map<String, Number> sortReadings(Sample sample) {
		if (sample == null)
			throw new NullPointerException();
		return new TreeMap<>(sample.readings());
	}
	
}
