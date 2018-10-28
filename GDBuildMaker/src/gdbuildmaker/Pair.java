package gdbuildmaker;

import java.util.Objects;

public class Pair<L, R> {
	private final L l;
	private final R r;
	
	public static <L, R> Pair<L, R> of(L l, R r) {
		return new Pair<L, R>(l, r);
	}
	
	public Pair(L left, R right) {
		this.l = left;
		this.r = right;
	}
	
	public L getLeft() { return l; }
	public R getRight() { return r; }
	
	public int hashCode() {
		return (l == null ? 0 : l.hashCode()) ^ (r == null ? 0 : r.hashCode());
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof Pair)) {
			return false;
		}
		Pair<?, ?> p = (Pair<?, ?>) o;
		return Objects.equals(p.l, l) && Objects.equals(p.r, r);
	}
}
