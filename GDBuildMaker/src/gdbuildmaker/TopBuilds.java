package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TopBuilds {
	private static final int DEFAULT_SIZE = 20;
	
	private static class Build implements Comparable<Build> {
		private Collection<Constellation> build;
		private Map<Constellation, Integer> partials;
		private Double value;
		
		private Build(Collection<Constellation> build,
				Map<Constellation, Integer> partials, double value) {
			this.build = build;
			this.partials = partials;
			this.value = value;
		}
		
		public int compareTo(Build o) {
			return this.value.compareTo(o.value);
		}
		
		public String toString() {
			return String.format("%.2f-%s%s", value, build, partials);
		}
	}
	
	private static TopBuilds instance = new TopBuilds(DEFAULT_SIZE);
	
	public static TopBuilds getInstance() {
		return instance;
	}
	
	public static void reset() {
		reset(DEFAULT_SIZE);
	}
	
	public static void reset(int maxSize) {
		instance = new TopBuilds(maxSize);
	}
	
	private final Lock lock;
	private final List<Build> topBuilds;
	
	private TopBuilds(int maxBuilds) {
		lock = new ReentrantLock();
		topBuilds = new ArrayList<Build>(maxBuilds);
		
		for (int i = 0; i < maxBuilds; i++) {
			topBuilds.add(new Build(new ArrayList<Constellation>(),
					new HashMap<Constellation, Integer>(), 0.0));
		}
	}
	
	public void submit(Collection<Constellation> build,
			Map<Constellation, Integer> partials, double value) {
		lock.lock();
		
		if (value > topBuilds.get(0).value) {
			List<Constellation> topBuild = new ArrayList<Constellation>(build);
			Collections.sort(topBuild, new Comparator<Constellation>() {
				public int compare(Constellation c1, Constellation c2) {
					return c1.getRequirement().total() - c2.getRequirement().total();
				}
			});
			topBuilds.set(0, new Build(
					topBuild,
					new HashMap<Constellation, Integer>(partials),
					value));
			Collections.sort(topBuilds);
		}
		
		lock.unlock();
	}
	
	public String buildsString() {
		// Lock topBuilds for as short a time as possible
		Build[] snapshot = new Build[topBuilds.size()];
		lock.lock();
		topBuilds.toArray(snapshot);
		lock.unlock();
		
		StringBuilder string = new StringBuilder();
		// Build string in reverse order so highest values are at the top
		for (int i = snapshot.length-1; i >= 0; i--) {
			string.append(snapshot[i] + "\n");
		}
		return string.toString();
	}
}
