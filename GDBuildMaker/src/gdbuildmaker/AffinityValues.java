package gdbuildmaker;

import java.util.Arrays;

public class AffinityValues {
	private int[] affinityValues;
	
	public AffinityValues() {
		affinityValues = new int[Affinity.values().length];
	}
	
	public String toString() {
		return Arrays.toString(affinityValues);
	}
	
	public void setValue(Affinity a, int v) {
		affinityValues[a.ordinal()] = v;
	}
	
	public int getValue(Affinity a) {
		return affinityValues[a.ordinal()];
	}
	
	public boolean fullfills(AffinityValues requirement) {
		for(Affinity a : Affinity.values()) {
			if(this.affinityValues[a.ordinal()] < requirement.affinityValues[a.ordinal()]) {
				return false;
			}
		}
		return true;
	}
	
	public void add(AffinityValues other) {
		for(Affinity a : Affinity.values()) {
			this.affinityValues[a.ordinal()] += other.affinityValues[a.ordinal()];
		}
	}
	
	public void subtract(AffinityValues other) {
		for(Affinity a : Affinity.values()) {
			this.affinityValues[a.ordinal()] -= other.affinityValues[a.ordinal()];
		}
	}
	
	public AffinityValues plus(AffinityValues other) {
		AffinityValues rav = new AffinityValues();
		rav.add(this);
		rav.add(other);
		return rav;
	}
	
	public AffinityValues minus(AffinityValues other) {
		AffinityValues rav = new AffinityValues();
		rav.add(this);
		rav.subtract(other);
		return rav;
	}
	
	public int total() {
		int total = 0;
		for (int i = 0; i < this.affinityValues.length; i++) {
			total += this.affinityValues[i];
		}
		return total;
	}
}
