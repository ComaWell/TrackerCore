package us.conian;

import java.time.LocalDateTime;
import java.util.*;

public class Sample implements Iterable<Sample.Reading> {
	
	public static record Reading(String name, double value) implements Comparable<Reading> {
		
		public Reading(String name, double value) {
			if (name == null)
				throw new NullPointerException();
			if (value < 0)
				throw new IllegalArgumentException("The value must be positive");
			this.name = name;
			this.value = value;
		}
		
		@Override
		public int compareTo(Reading other) {
			if (other == null)
				throw new NullPointerException();
			int nameComp = name.compareToIgnoreCase(other.name);
			if (nameComp != 0)
				return nameComp;
			return Double.compare(value, other.value);
		}
		
		public int compareValues(Reading other) {
			if (other == null)
				throw new NullPointerException();
			return Double.compare(value, other.value);
		}
		
		@Override
		public String toString() {
			return name + ", " + (SampleUtils.isWhole(value) 
					? SampleUtils.LONG_FORMAT.format(value) 
					: SampleUtils.DOUBLE_FORMAT.format(value));
		}

		@Override
		public boolean equals(Object obj) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public int hashCode() {
			// TODO Auto-generated method stub
			return 0;
		}
		
	}
	
	private final LocalDateTime timestamp;
	private final Reading[] readings;
	
	private final int hashCode;
	
	public Sample(LocalDateTime timestamp, Reading[] readings) {
		if (timestamp == null || readings == null)
			throw new NullPointerException();
		this.timestamp = timestamp;
		this.readings = readings.clone();
		Arrays.sort(readings);
		this.hashCode = computeHashCode();
	}
	
	public LocalDateTime timestamp() {
		return timestamp;
	}
	
	public int numReadings() {
		return readings.length;
	}
	
	public Reading[] readings() {
		return readings.clone();
	}
	
	public Reading getReading(String name) {
		if (name == null)
			throw new NullPointerException();
		for (Reading r : readings)
			if (r.name.equalsIgnoreCase(name))
				return r;
		return null;
	}
	
	public boolean hasReading(String name) {
		if (name == null)
			throw new NullPointerException();
		return getReading(name) != null;
	}
	
	@Override
	public Iterator<Reading> iterator() {
		return new ReadingIterator();
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	private int computeHashCode() {
		int hashCode = timestamp.hashCode();
		for (Reading r : readings) {
			hashCode ^= r.hashCode();
		}
		return hashCode;
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof Sample s
				&& s.timestamp().equals(timestamp)
				&& Arrays.equals(s.readings, readings);
	}
	
	private class ReadingIterator implements Iterator<Reading> {
		
		int index = 0;
		
		@Override
		public boolean hasNext() {
			return index < readings.length; 
		}
		
		@Override
		public Reading next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException();
			return readings[index++];
		}
		
	}

}
