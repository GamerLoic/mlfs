package mlfs.chineseSeg.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mlfs.crf.Features;
import mlfs.crf.TemplateHandler;
import mlfs.crf.cache.FeatureCacher;
import mlfs.crf.graph.Edge;
import mlfs.crf.graph.Graph;
import mlfs.crf.graph.Node;
import mlfs.crf.model.CRFEvent;

public class DebugHelper {

	
	public static void saveToCache(CRFEvent e, int numTag, TemplateHandler template, Map<String, Integer> featIdMap)
	{
		FeatureCacher cacher = FeatureCacher.getInstance();
		
		e.FEATURE_CACHE_POS = cacher.size();
			
		int len = e.labels.length;
		List<Integer> feats = null;
		for (int i=0; i<len; i++)//unigram
		{
			feats = new ArrayList<Integer>();
			List<String> unigramPred = template.getUnigramPred(e, i);
			for (String predicate : unigramPred)
			{
				String unigramFeat = predicate;
					
				if (featIdMap.containsKey(unigramFeat))
					feats.add(featIdMap.get(unigramFeat));
			}
			cacher.add(feats);
		}
		for (int i=1; i<len; i++)//bigram
		{
			List<String> bigramPred = template.getBigramPred(e, i);
			for (int preTag=0; preTag<numTag; preTag++)
			{
				feats = new ArrayList<Integer>();
				for (String predicate : bigramPred)
				{
					String bigramFeat = predicate + Features.FEATURE_JOIN +preTag;
						
					if (featIdMap.containsKey(bigramFeat))
						feats.add(featIdMap.get(bigramFeat));
				}
				cacher.add(feats);
			}
		}
		e.charFeat = null;
	}
	
	/**
	 * Label.
	 *
	 * @param e the e
	 * @param numTag the num tag
	 * @param parameters the parameters
	 * @return 标注的tag序列，是tag对应的int形式
	 */
	public static int[] label(CRFEvent e, int numTag, double[] parameters)
	{
		Graph graph = Graph.buildGraph(e, numTag, parameters);
		
		int len = e.labels.length;
		
		double[][] delta = new double[numTag][len];
		int[][] phi = new int[numTag][len];
		
		Node[][] nodes = graph.getNodes();
		int lastIdx = -1;
		for (int i=0; i<len; i++)
		{
			lastIdx = -1;
			for (int j=0; j<numTag; j++)
			{
				double max = Double.NEGATIVE_INFINITY;
				Node node = nodes[i][j];
				List<Edge> leftNodes = node.m_ledge;
				for (Edge edge : leftNodes)
				{
					double v = delta[edge.m_lnode.m_y][i-1] + edge.getBigramProb() + node.getUnigramProb();
					if (v > max)
					{
						max = v;
						lastIdx = edge.m_lnode.m_y;
					}
				}
				phi[j][i] = lastIdx;
				delta[j][i] = lastIdx==-1 ? node.getUnigramProb() : max;
			}
		}
		
		double max = Double.NEGATIVE_INFINITY;
		for (int tag=0; tag<numTag; tag++)
		{
			if (delta[tag][len-1] > max)
			{
				max = delta[tag][len-1];
				lastIdx = tag;
			}
		}
		
		int[] stack = new int[len];
		stack[len-1] = lastIdx;
		for (int t = len-1; t>0; t--)
			stack[t-1] = phi[stack[t]][t];
		
		return stack;
	}
	
	public static double evaluate(List<CRFEvent> events, int numTag, double[] parameters)
	{
		int t = 0, f = 0;
		int sz = events.size();
		for (int i=0; i<sz; i++)
		{
			CRFEvent e = events.get(i);
			int[] prediction = label(e, numTag, parameters);
			int seqLen = prediction.length;
			for (int j=0; j<seqLen; j++)
			{
				if (prediction[j] == e.labels[j])
					t++;
				else
					f++;
			}
		}
		return 1.0*t/(t+f);
	}
}