package istc.bigdawg.signature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import convenience.RTED;
import istc.bigdawg.packages.QueryContainerForCommonDatabase;
import istc.bigdawg.plan.operators.Operator;
import istc.bigdawg.signature.builder.ArraySignatureBuilder;
import istc.bigdawg.signature.builder.RelationalSignatureBuilder;
import istc.bigdawg.utils.IslandsAndCast.Scope;

public class Signature {
	
	private static String fieldSeparator = "|||||";
	private static String elementSeparator = "&&&&&";
	
	private Scope island;
	private String sig1;
	private List<String> sig2;
	private List<String> sig3;
	private String query;
	private List<String> sig4k;
//	private String identifier; 
	
//	private static Pattern possibleObjectsPattern	= Pattern.compile("[_@a-zA-Z0-9]+");
//	private static Pattern tagPattern				= Pattern.compile("BIGDAWGTAG_[0-9_]+");
	
	/**
	 * Construct a signature of three parts: sig1 tells about the structure, sig2 are all object references, sig3 are all constants
	 * @param cc
	 * @param query
	 * @param island
	 * @throws Exception
	 */
	public Signature(String query, Scope island, Operator root, Map<String, QueryContainerForCommonDatabase> container) throws Exception {
		
		if (island.equals(Scope.RELATIONAL)){
			setSig2(RelationalSignatureBuilder.sig2(query));
			setSig3(RelationalSignatureBuilder.sig3(query));
		} else if (island.equals(Scope.ARRAY)) {
			setSig2(ArraySignatureBuilder.sig2(query));
			setSig3(ArraySignatureBuilder.sig3(query));
		} else {
			throw new Exception("Invalid Signature island input: "+island);
		}
		
		
		List<String> cs = new ArrayList<>();
		for (String s : container.keySet()) 
			cs.add(container.get(s).generateTreeExpression());
		setSig4k(cs);
		setSig1(root.getTreeRepresentation(true));
		
		this.setQuery(query);
		this.setIsland(island);
//		this.setIdentifier(identifier);
	}
	
	
	public Signature(String s) throws Exception{

		List<String> parsed = Arrays.asList(s.split(fieldSeparator));
		if (parsed.size() != 5 && parsed.size() != 6) {
			throw new Exception("Ill-formed input string; cannot recover signature; String: "+s);
		}
		try {
			this.island = Scope.valueOf(parsed.get(0));
			this.sig1 = new String(parsed.get(1));
			this.sig2 = Arrays.asList(parsed.get(2).split(elementSeparator));
			this.sig3 = Arrays.asList(parsed.get(3).split(elementSeparator));
			this.query = new String(parsed.get(4));
			if (parsed.size() == 5)
				this.sig4k = new ArrayList<>();
			else
				this.sig4k = Arrays.asList(parsed.get(5).split(elementSeparator));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Ill-formed input string; cannot recover signature; String: "+s);
		}
	}
	
	public static double getTreeEditDistance(String s1, String s2) {
		return RTED.computeDistance(s1, s2);
	}
	
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		sb.append("Signature:\n");
		sb.append("Island       : ").append(island.toString()).append('\n');
		sb.append("Signature 1  : ").append(sig1.toString()).append('\n');
		sb.append("Signature 2  : ").append(sig2.toString()).append('\n');
		sb.append("Signature 3  : ").append(sig3.toString()).append('\n');
		sb.append("Query        : ").append(query).append('\n');
		sb.append("Signature 4-k: ").append(sig4k.toString()).append('\n');
		
		return sb.toString();
	}
	public void print() {
		System.out.println(this.toString());
	}
	
	public String getSig1() {
		return sig1;
	}

	public void setSig1(String sig1) {
		this.sig1 = sig1;
	}

	public List<String> getSig2() {
		return sig2;
	}

	public void setSig2(List<String> sig2) {
		this.sig2 = sig2;
	}

	public List<String> getSig3() {
		return sig3;
	}

	public void setSig3(List<String> sig3) {
		this.sig3 = sig3;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public Scope getIsland() {
		return island;
	}
	
	private void setIsland(Scope island) {
		this.island = island;
	}

	public List<String> getSig4k() {
		return sig4k;
	}

	public void setSig4k(List<String> sig4k) {
		this.sig4k = sig4k;
	}
	
	public String toRecoverableString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(island.toString());
		sb.append(fieldSeparator).append(sig1);
		sb.append(fieldSeparator).append(String.join(elementSeparator, sig2));
		sb.append(fieldSeparator).append(String.join(elementSeparator, sig3));
		sb.append(fieldSeparator).append(query);
		
		if (sig4k.size() > 0)
			sb.append(fieldSeparator).append(String.join(elementSeparator, sig4k));
		
		return sb.toString();
	}
	
	
	public double compare(Signature sig) {
		double dist = 0;
		
		List<String> l2;
		List<String> l4k2 = new ArrayList<>(sig.sig4k);
		int size;
		
		// sig1
		dist = getTreeEditDistance(sig1, sig.sig1);
		
		// sig2
		if (sig2.size() > sig.sig2.size()) {
			l2 = new ArrayList<>(sig2);
			l2.retainAll(sig.sig2);
			size = sig2.size();
		} else { 
			l2 = new ArrayList<>(sig.sig2);
			l2.retainAll(sig2);
			size = sig.sig2.size();
		}
		dist *= ((double)l2.size()) / size;
		
		// sig3
		dist += (sig3.size() > sig.sig3.size()) ? sig3.size() - sig.sig3.size() : sig.sig3.size() - sig3.size();
		
		// sig4k
		dist += sig4k.size() < sig.sig4k.size() ? sig.sig4k.size() - sig4k.size() : sig4k.size() - sig.sig4k.size();
		for (int i = 0 ; i < sig4k.size() ; i++ ) {
			double result = Double.MAX_VALUE;
			int j = 0;
			int holder = -1;
			while (!l4k2.isEmpty() && j < l4k2.size()) {
				double temp = getTreeEditDistance(sig4k.get(i), l4k2.get(j));
				if (temp < result) {
					result = temp;
					holder = j;
				}
				j++;
			}
			if (holder > 0) { 
				l4k2.remove(holder);
				dist += result;
			} else 
				break;
		}
		
		return dist;
	}
	
	
	
}