package us.conian;

import java.io.FileFilter;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class CSVUtils {
	
	public static final String FILE_EXTENSION = ".csv";
	
	public static final FileFilter FILE_FILTER = (file) -> file.isFile() && file.getName().endsWith(FILE_EXTENSION);
	
	public static final String SEPARATOR = ", ";
	
	public static <V> String toCSVString(V[] values) {
		return toCSVString(values, true);
	}
	
	public static <V> String toCSVString(V[] values, boolean singleLine) {
		if (values == null)
			throw new NullPointerException();
		if (singleLine) {
			StringJoiner sj = new StringJoiner(",");
			for (V value : values) {
				if (value == null)
					throw new NullPointerException();
				sj.add(value.toString());
			}
			return sj.toString();
		}
		else {
			StringBuilder sb = new StringBuilder();
			for (V value : values) {
				if (value == null)
					throw new NullPointerException();
				sb.append(value.toString())
				.append("\n");
			}
			return sb.toString();
		}
	}
	
	//the generics are only included because the compiler complains
	//about the iterator types otherwise
	public static <K, V> String toCSVString(Map<K, V> map) {
		return toCSVString(map, Objects::toString, Objects::toString);
	}
	
	public static <K, V> String toCSVString(Map<K, V> map, Function<? super K, String> keyConverter, Function<? super V, String> valueConverter) {
		if (map == null)
			throw new NullPointerException();
		StringBuilder sb = new StringBuilder();
		Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<K, V> entry = it.next();
			sb.append(keyConverter.apply(entry.getKey()))
			.append(SEPARATOR)
			.append(valueConverter.apply(entry.getValue()));
			if (it.hasNext())
				sb.append("\n");
		}
		return sb.toString();
	}
	
	//group 1 = key, group 2 = value
	public static final Pattern ENTRY_CSV_PATTERN = Pattern.compile("^(.+), (.+)$");
	
	public static <K, V> Map<K, V> fromCSVString(String mapCSV, Function<String, ? extends K> keyConverter, Function<String, ? extends V> valueConverter) {
		if (mapCSV == null || keyConverter == null
				|| valueConverter == null)
			throw new NullPointerException();
		if (mapCSV.isBlank())
			return Map.of();
		return mapCSV.lines()
				.filter((line) -> !line.isBlank())
				.map((line) -> {
					Matcher matcher = ENTRY_CSV_PATTERN.matcher(line.strip());
					if (!matcher.matches())
						//TODO: Create CSVParseException
						throw new RuntimeException("The following line does not consitute a Map entry: \"" + line + "\"");
					String key = matcher.group(1);
					String value = matcher.group(2);
					return Map.entry(keyConverter.apply(key), valueConverter.apply(value));
				})
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
	}
	
	public static <K, V> Map<K, V> fromCSVEntries(List<String> csvEntries, Function<String, ? extends K> keyConverter, Function<String, ? extends V> valueConverter) {
		Map<K, V> map = new HashMap<>();
		for (String line : csvEntries) {
			Matcher matcher = ENTRY_CSV_PATTERN.matcher(line.strip());
			if (!matcher.matches())
				//TODO: Create CSVParseException
				throw new RuntimeException("The following line does not consitute a Map entry: \"" + line + "\"");
			String key = matcher.group(1);
			String value = matcher.group(2);
			map.put(keyConverter.apply(key), valueConverter.apply(value));
		}
		return map;
	}
}
