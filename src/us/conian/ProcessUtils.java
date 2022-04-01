package us.conian;

import java.nio.file.Paths;
import java.util.*;

public class ProcessUtils {

	public static List<ProcessHandle> pollProcesses() {
		return ProcessHandle.allProcesses()
				.filter((p) -> p.info().command().isPresent())
				.toList();
	}
	
	public static String getProcessName(ProcessHandle handle) {
		Optional<String> command = handle.info().command();
		return command.isEmpty() 
				? "unknown" 
				: Paths.get(command.get()).getFileName().toString().replace(".exe", "");
	}
	
	public static Map<ProcessHandle, String> mapCounterNames(List<ProcessHandle> processes) {
		if (processes == null)
			throw new NullPointerException();
		if (processes.isEmpty())
			return Map.of();
		Map<String, Integer> counters = new HashMap<>();
		Map<ProcessHandle, String> counterNames = new HashMap<>();
		for (ProcessHandle process : processes) {
			if (process.info().command().isEmpty())
				continue;
			String path = process.info().command().get();
			int count;
			if (!counters.containsKey(path)) {
				count = 0;
				counters.put(path, 1);
			}
			else {
				count = counters.get(path);
				counters.replace(path, count + 1);
			}
			String counterName = getProcessName(process) + (count == 0 ? "" : "#" + count);
			if (counterNames.putIfAbsent(process, counterName) != null)
				throw new IllegalArgumentException("Duplicate process found: " + process.pid());
		}
		return counterNames;
	}
	
}
