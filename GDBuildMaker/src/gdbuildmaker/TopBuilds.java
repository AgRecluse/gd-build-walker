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
	private static final int DEFAULT_SIZE = 100;
	
	public void reset() {
		reset(DEFAULT_SIZE);
	}
	
	public void reset(int maxSize) {
		topBuilds.clear();
	}
	
	private final Lock lock;
	private final List<Build> topBuilds;
	
	public TopBuilds() { this(DEFAULT_SIZE); }
	
	public TopBuilds(int maxBuilds) {
		lock = new ReentrantLock();
		topBuilds = new ArrayList<Build>(maxBuilds);
		
		// Fill the top builds list with empty, zero value builds
		for (int i = 0; i < maxBuilds; i++) {
			topBuilds.add(new Build(new ArrayList<Constellation>(),
					new HashMap<Constellation, Integer>(), 0.0));
		}
	}
	
	public void submit(Collection<Constellation> constellations,
			Map<Constellation, Integer> partials, double value) {
		lock.lock();
		
		if (value > topBuilds.get(0).getValue()) {
			
			// Order the build constellations from least to most requirements
			List<Constellation> orderedConstellations = new ArrayList<Constellation>(constellations);
			Collections.sort(orderedConstellations, new Comparator<Constellation>() {
				public int compare(Constellation c1, Constellation c2) {
					return c1.getRequirement().total() - c2.getRequirement().total();
				}
			});
			
			// Replace the lowest value build with the new top build
			topBuilds.set(0, new Build(
					orderedConstellations,
					new HashMap<Constellation, Integer>(partials),
					value));
			
			// Re-sort the build list from lowest to highest value
			Collections.sort(topBuilds);
		}
		
		lock.unlock();
	}
	
	public List<Build> getBuilds() {
		lock.lock();
		List<Build> highToLow = new ArrayList<Build>(topBuilds);
		lock.unlock();
		
		Collections.reverse(highToLow);
		return highToLow;
	}
}
