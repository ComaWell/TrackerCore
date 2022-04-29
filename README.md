TackerCore

This project contains the core library for process tracking and storing. This project is primarily used by ProcessTracker (https://github.com/ComaWell/ProcessTracker) and ProcessModeler (https://github.com/ComaWell/ProcessModeler). The primary objects defined in TrackerCore are Sample and SampleSet. 

A Sample represents a single sample gathered for a single program. The way samples are gathered are by executing the Get-Counter Powershell command for all currently running processes on a Windows machine and capturing its output. Each process has a set of counters associated with it by the operating system, each relating to a metric it is tracking (for example, the amount of memory it is using). The Get-Counter command retrieves the current value of each counter for each process and outputs them at a set interval. A Sample corresponds to all of the recorded values for a single program at a single interval. For example, here is a CSV representation of a single Sample captured from an instance of ''.exe:

2022/04/01 15:21:28
io data operations/sec, 0
io write operations/sec, 0
handle count, 322
% user time, 0
private bytes, 7921664
page faults/sec, 0
working set - private, 5054464
io write bytes/sec, 0
thread count, 9
% privileged time, 0
io read operations/sec, 0
io read bytes/sec, 0
id process, 14052
virtual bytes peak, 2203732688896
pool nonpaged bytes, 25424
pool paged bytes, 168928
io other operations/sec, 0
elapsed time, 334709.7669058
page file bytes, 7921664
virtual bytes, 2203719733248
creating process id, 1388
io data bytes/sec, 0
% processor time, 0
page file bytes peak, 11259904
working set peak, 20959232
priority base, 8
working set, 15679488
io other bytes/sec, 0

A Sample instance contains 3 fields: a timestamp, corresponding to the date and time the Sample was recorded, an array of Reading objects (a nested record within Sample that contains the name of a counter and its corresponding value) and a boolean indicating whether a Sample is 'dead.' A Sample is 'dead' if it was taken after a process finished executing; it is characterized by all of its Readings having a value of 0. Dead Samples are a common occurence for Samples gathered over a longer period of time- it is important that they be disregarded for computations regarding the HMM.

A SampleSet represents a chronological group of Samples. In order for the data in a SampleSet to be meaningful, the Samples will typically all be captured from the same process, and will originate from the same execution of Get-Counter. In other words, a valid SampleSet could be thought of as all of the Samples gathered for a single process in a single execution, ordered chronologically. For example, here is a CSV representation of the SampleSet from which the preceding example originated:

2022/04/01 15:21:28
io data operations/sec, 0
io write operations/sec, 0
handle count, 322
% user time, 0
private bytes, 7921664
page faults/sec, 0
working set - private, 5054464
io write bytes/sec, 0
thread count, 9
% privileged time, 0
io read operations/sec, 0
io read bytes/sec, 0
id process, 14052
virtual bytes peak, 2203732688896
pool nonpaged bytes, 25424
pool paged bytes, 168928
io other operations/sec, 0
elapsed time, 334709.7669058
page file bytes, 7921664
virtual bytes, 2203719733248
creating process id, 1388
io data bytes/sec, 0
% processor time, 0
page file bytes peak, 11259904
working set peak, 20959232
priority base, 8
working set, 15679488
io other bytes/sec, 0
2022/04/01 15:21:31
io data operations/sec, 0
io write operations/sec, 0
handle count, 322
% user time, 0
private bytes, 7921664
page faults/sec, 0
working set - private, 5054464
io write bytes/sec, 0
thread count, 9
% privileged time, 0
io read operations/sec, 0
io read bytes/sec, 0
id process, 14052
virtual bytes peak, 2203732688896
pool nonpaged bytes, 25424
pool paged bytes, 168928
io other operations/sec, 0
elapsed time, 334713.2268247
page file bytes, 7921664
virtual bytes, 2203719733248
creating process id, 1388
io data bytes/sec, 0
% processor time, 0
page file bytes peak, 11259904
working set peak, 20959232
priority base, 8
working set, 15679488
io other bytes/sec, 0

It is important to note that both Sample and SampleSet are immutable objects.

There are two counters, 'id process' and 'creating process id', that are runtime-specific and do not actually correspond to a tracked metric. For SampleSet validation purposes these readings are retained, however it is important that they are not used for computations regarding the HMM. These readings are categorized as 'meta' readings, and can be disregarded by utilizing Sample instances returned from Sample#minusMetaReadings().

Within the SampleSet class is a nested class Meta, which contains useful, pre-computed data for SampleSets, such as the min, max, and mean of its Samples, the Duration between each Sample, and the covariance matrix for the Samples (which is currently unused).

The rest of the library primarily comprises of utility classes for command line tools, converting Samples to and from CSV Strings, creating Get-Counter powershell commands, and parsing raw Get-Counter output into Samples.
