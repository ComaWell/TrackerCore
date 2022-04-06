package us.conian;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class SampleSet implements Iterable<Sample> {
	
	private final String counterName;
	private final String processName;
	
	private final Sample[] samples;
	
	private final Meta meta;
	
	//precomputed because the set is immutable
	private final int hashCode;
	
	public SampleSet(String counterName, List<Sample> samples, boolean assertGenuine, boolean assertComplete) {
		if (counterName == null || samples == null)
			throw new NullPointerException();
		if (samples.size() < 2)
			throw new IllegalArgumentException("A valid SampleSet must contain at least 2 Samples");
		this.counterName = counterName;
		this.processName = counterName.split("#")[0];//TODO: Test
		this.samples = samples.toArray(Sample[]::new);
		Arrays.sort(this.samples, (s1, s2) -> s1.timestamp().compareTo(s2.timestamp()));
		this.meta = new Meta(assertGenuine, assertComplete);
		this.hashCode = calcHashCode();
	}
	
	public SampleSet(String counterName, List<Sample> samples) {
		this(counterName, samples, true, true);
	}
	
	/* The difference between counterName and processName is
	 * that the counterName potentially has a suffix labeling
	 * it distinctly from duplicate processes that were running
	 * at the time of sampling, whereas processName is just the
	 * name of the process.
	 * 
	 * For example, if there were multiple Chrome tabs open while
	 * sampling, one Chrome tab's process might have a counterName
	 * of "chrome#1", and another might have a counterName of "chrome#4",
	 * but both would have a processName of "chrome". The number in
	 * the counterName should be treated similar to a process ID in that
	 * it's unique only in the context of the samples that were gathered
	 * at the same time and on the same PC as it. For this reason, one
	 * SampleSet with a counterName of "chrome#1" does not necessarily
	 * correspond to the same process as another SampleSet with a counterName
	 * of "chrome#1". When dealing with SampleSets gathered at different
	 * times or by different machines, processName should be used instead.
	 */
	
	public String counterName() {
		return counterName;
	}
	
	public String processName() {
		return processName;
	}
	
	public int size() {
		return samples.length;
	}
	
	public Sample get(int index) throws IndexOutOfBoundsException {
		if (index < 0 || index >= samples.length)
			throw new IndexOutOfBoundsException(index);
		return samples[index];
	}
	
	public Sample[] samples() {
		return samples.clone();
	}
	
	public Meta meta() {
		return meta;
	}

	@Override
	public Iterator<Sample> iterator() {
		return new SampleIterator();
	}
	
	/* NOTE: Only the counterName and samples array are used for hashCode and
	 * equals. All other fields are derived from these, so we can safely assume
	 * that if they are equal, then the other fields are equal as well.
	 */
	private int calcHashCode() {
		return counterName.hashCode() ^ Arrays.hashCode(samples); 
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof SampleSet s
				&& s.counterName.equals(counterName)
				&& Arrays.equals(s.samples, samples);
	}
	
	private class SampleIterator implements Iterator<Sample> {
		
		int index = 0;
		
		@Override
		public boolean hasNext() {
			return index < samples.length; 
		}
		
		@Override
		public Sample next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException();
			return samples[index++];
		}
		
	}
	
	/* Since SampleSets are immutable, we can pre-compute some information
	 * about the set on instantiation, to reduce the load later down the line.
	 * 
	 * The Meta class contains said pre-computed information.
	 * 
	 * genuine: Whether the SampleSet is "genuine", in that all of the samples
	 * are of the same PID, and the Sample timestamps are relatively regular 
	 * and evenly distributed, indicating we can reasonably assume they were
	 * gathered on a single machine and over a single execution of ProcessTracker.
	 * A SampleSet will automatically be considered ingenuine if a sample does not
	 * have a reading for the process id, or if the value of the reading is negative.
	 * 
	 * complete: Whether every sample in the set contains all of the same reading
	 * entries as each other. If false, then one or more sample in the set either
	 * contains additional reading(s) that other samples do not have, or is missing
	 * reading(s) that other samples have. This assertion is only useful in the
	 * scope of the set by itself- a SampleSet in which every sample has 0 readings is
	 * equally as "complete" as a SampleSet in which every sample has readings
	 * for every available Windows performance counter.
	 * 
	 */
	
	public class Meta {
		
		private static final String PID_READING = "id process";
		
		/* The amount of times longer the longest interval can be
		 * than the average interval before being flagged as
		 * ingenuine. Honestly, this number was pulled out of my ass.
		 */
		private static final double INTERVAL_OUTLIER_TOLERANCE = 3.0;
		
		/* The durations between each sample. The array is chronologically
		 * ordered, and the elements correspond to the samples such that
		 * intervals[0] is the interval between samples[0] and samples[1],
		 * intervals[1] is the interval between samples[1] and samples[2], etc.
		 */
		private final Duration[] intervals;
		private final Duration minInterval;
		private final Duration maxInterval;
		private final Duration meanInterval;
		
		private final Sample minSample;
		private final Sample maxSample;
		private final Sample meanSample;
		
		private final String genuine;
		private final boolean complete;
		
		private final double[][] covMatrix;
		
		/* Order of computation:
		 * 
		 * 1. Intervals
		 * 2. Complete
		 * 3. Min/Max/Means
		 * 4. Genuine
		 * 
		 */
		
		private Meta(boolean assertGenuine, boolean assertComplete) {
			this.intervals = calcIntervals();
			this.complete = calcComplete();
			if (!complete && assertComplete)
				throw new IllegalArgumentException("The given Samples are not complete");
			Duration[] minMaxMeanIntervals = calcMinMaxMeanIntervals();
			this.minInterval = minMaxMeanIntervals[0];
			this.maxInterval = minMaxMeanIntervals[1];
			this.meanInterval = minMaxMeanIntervals[2];
			Sample[] minMaxMeanSamples = calcMinMaxMeanSamples();
			this.minSample = minMaxMeanSamples[0];
			this.maxSample = minMaxMeanSamples[1];
			this.meanSample = minMaxMeanSamples[2];
			this.genuine = calcGenuine();
			if (!isGenuine() && assertGenuine)
				throw new IllegalArgumentException("The given SampleSet is not genuine (" + genuine + ")");
			this.covMatrix = calcCovMatrix();
		}
		
		public Duration[] intervals() {
			return intervals.clone();
		}
		
		public Duration minInterval() {
			return minInterval;
		}
		
		public Duration maxInterval() {
			return maxInterval();
		}
		
		public Duration meanInterval() {
			return meanInterval;
		}
		
		public Sample minSample() {
			return minSample;
		}
		
		public Sample maxSample() {
			return maxSample;
		}
		
		//NOTE: The meanSample's timestamp does not accurately
		//reflect the mean of the timestamps. I did not think that was
		//needed for the calculations, but if it is I can add it.
		public Sample meanSample() {
			return meanSample;
		}
		
		public boolean isGenuine() {
			return genuine == null;
		}
		
		public String ingenuineReason() {
			return genuine;
		}
		
		public boolean isComplete() {
			return complete;
		}
		
		public double[][] getCovMatrix(){
			return covMatrix.clone();
		}
		
		private boolean calcComplete() {
			Set<String> readings = Arrays.stream(samples[0].readings())
					.map(Sample.Reading::name)
					.collect(Collectors.toSet());
			for (int i = 1; i < samples.length; i++) {
				Sample sample = samples[i];
				if (sample.numReadings() != readings.size())
					return false;
				for (Sample.Reading reading : sample)
					if (!readings.contains(reading.name()))
						return false;
			}
			return true;
		}
		
		private String calcGenuine() {
			long pid = -1;
			boolean assigned = false;
			boolean died = false;
			for (Sample s : samples) {
				if (s.isDeadSample()) {
					died = true;
					continue;
				}
				/* Essentially dead samples were gathered at one point, indicating the
				 * process died, but then non-dead samples were gathered afterwards,
				 * which shouldn't be possible in a genuine SampleSet (even if the process
				 * was restarted again later, it would almost certainly have a different
				 * PID and counterName)
				 */
				else if (died)
					return "contains non-dead samples that were taken after dead samples";
				if (!s.hasReading(PID_READING))
					return "missing 1 or more PID reading";
				long p = Double.doubleToLongBits(s.getReading(PID_READING).value());
				//pid's can never be negative
				if (p < 0)
					return "illegal PID value";
				if (!assigned) {
					pid = p;
					assigned = true;
				}
				else if (pid != p)
					return "contains multiple PID values";
			}
			long min = minInterval.getSeconds() + minInterval.getNano();
			long max = maxInterval.getSeconds() + maxInterval.getNano();
			long mean = meanInterval.getSeconds() + meanInterval.getNano();
			//if the ratio of max interval : mean interval or the ratio of
			//mean interval : min interval is greater than INTERVAL_OUTLIER_TOLERANCE,
			//we are assuming that the data was not gathered in the same runtime
			if (max / mean > INTERVAL_OUTLIER_TOLERANCE)
				return "max interval above interval tolerance threshold";
			if (mean / min > INTERVAL_OUTLIER_TOLERANCE)
				return "min interval below interval tolerance threshold";
			return null;
		}
		
		private Duration[] calcIntervals() {
			List<Duration> intervals = new ArrayList<>();
			for (int i = 1; i < samples.length; i++) {
				intervals.add(Duration.between(samples[i - 1].timestamp(), samples[i].timestamp()).abs());
			}
			return intervals.toArray(Duration[]::new);
		}
		
		private Duration[] calcMinMaxMeanIntervals() {
			Duration min = intervals[0];
			Duration max = intervals[0];
			long avg = 0;
			for (Duration d : intervals) {
				if (d.compareTo(min) < 0)
					min = d;
				if (d.compareTo(max) > 0)
					max = d;
				avg += d.getSeconds() + d.getNano();
			}
			Duration average = Duration.ofNanos((avg / intervals.length));
			return new Duration[]{ min, max, average};
		}
		
		//Prepares a Map containing entries of all of the readings
		//with a value of 0
		private Map<String, List<Sample.Reading>> emptyReadings() {
			Map<String, List<Sample.Reading>> readings = new HashMap<>();
			if (complete) {
				for (Sample.Reading reading : samples[0]) {
					readings.put(reading.name(), new ArrayList<>());
				}
			}
			else {
				for (Sample s : samples) {
					for (Sample.Reading r : s) {
						readings.putIfAbsent(r.name(), new ArrayList<>());
					}
				}
			}
			return readings;
		}
		
		private static double meanReading(List<Sample.Reading> readings) {
			double total = 0;
			for (Sample.Reading r : readings)
				total += r.value();
			return total / readings.size();
		}
		
		//TODO: Do we need the average timestamp?
		private Sample[] calcMinMaxMeanSamples() {
			Map<String, List<Sample.Reading>> sorted = emptyReadings();
			Map<String, Sample.Reading> minReadings = new HashMap<>();
			Map<String, Sample.Reading> maxReadings = new HashMap<>();
			Map<String, Sample.Reading> meanReadings = new HashMap<>();
			LocalDateTime minTimestamp = null;
			LocalDateTime maxTimestamp = null;
			for (Sample s : samples) {
				for (Sample.Reading r : s) {
					String name = r.name();
					sorted.get(name).add(r);
					if (!minReadings.containsKey(name) || minReadings.get(name).compareValues(r) > 0)
						minReadings.put(name, r);
					if (!maxReadings.containsKey(name) || maxReadings.get(name).compareValues(r) < 0)
						maxReadings.put(name, r);
				}
				LocalDateTime timestamp = s.timestamp();
				if (minTimestamp == null || minTimestamp.compareTo(timestamp) > 0)
					minTimestamp = timestamp;
				if (maxTimestamp == null || maxTimestamp.compareTo(timestamp) < 0)
					maxTimestamp = timestamp;
			}
			for (Map.Entry<String, List<Sample.Reading>> entry : sorted.entrySet()) {
				String name = entry.getKey();
				meanReadings.put(name, new Sample.Reading(name, meanReading(entry.getValue())));
			}
			return new Sample[] {
					new Sample(minTimestamp, minReadings.values().toArray(Sample.Reading[]::new)),
					new Sample(maxTimestamp, maxReadings.values().toArray(Sample.Reading[]::new)),
					new Sample(LocalDateTime.MIN, meanReadings.values().toArray(Sample.Reading[]::new))
					};
		}
		
		private double[][] calcCovMatrix() {
			//this.meanSample
			if (complete) {
				int size = samples[0].numReadings();
				double[][] covMatrix = new double[size][size];
				for (int i = 0; i < size; i++) {
					for (int j=i; j < size; j++) {
						for (Sample s : samples) {
							double covVal = (s.get(i).value() - meanSample.get(i).value()) * (s.get(j).value() - meanSample.get(j).value()) / size;
							covMatrix[i][j] += covVal;
							if (i != j) {
								covMatrix[j][i] += covVal;
							}
						}
					}
				}
				
				return covMatrix;
			}
			else {
				throw new IllegalStateException();
			}
		}
		
	}

}
