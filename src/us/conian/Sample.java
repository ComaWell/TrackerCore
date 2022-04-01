package us.conian;

import java.time.*;
import java.util.*;

public final record Sample(LocalDateTime timestamp, Map<String, Number> readings) {
	
	public Sample(LocalDateTime timestamp, Map<String, Number> readings) {
		if (timestamp == null || readings == null)
			throw new NullPointerException();
		this.timestamp = timestamp;
		this.readings = Map.copyOf(readings);
	}

}
