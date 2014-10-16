import java.io.*;
import java.util.*;


public class run_tagger {
	static String sents_test = "";
	static String model_file = "";
	static String sents_out = "";

	static String[] pennTreeTags;
	static String[] pennTreeTagsPlus;
	static int posTransitions[][];
	static Hashtable<String, Integer> posTagsToIndex;
	static Set<String> vocab;
	static Hashtable<String, Hashtable<String, Integer>> posTagsToWordsAndCount;
	static float[][] posTransitionsProba;
	static Hashtable<String, Integer> tagCount;
	static int[][] results;
	
	private static String stripTrainingInstance(String instance) {
		String output = "";
		String[] tokens = instance.split(" ");
		boolean tagged = true;
		// First check if sentence is tagged
		for (String t : tokens) {
			if (!t.contains("/")) {
				tagged = false;
				break;
			}
		}
		if (!tagged) {
			return instance;
		} else {
			String[] temp;
			for (String t : tokens) {
				temp = t.split("/");
				for (int i = 0; i < temp.length - 1; i++) {
					output += temp[i] + " ";
				}
			}
			return output.trim();
		}
	}
	
	private static float writtenBellForWords(String observation,String tag){
		Hashtable<String,Integer> hashTemp = posTagsToWordsAndCount.get(tag);
		if(hashTemp.containsKey(observation)){
			float C_to = 0, C_t = 0, T_t = 0;
			C_to = hashTemp.get(observation);
			C_t = tagCount.get(tag);
			T_t = hashTemp.keySet().size();
			
			return C_to / (C_t + T_t);
		}else{
			float T_t = 0, C_t = 0, Z_t = 0;
			C_t = tagCount.get(tag);
			T_t = hashTemp.keySet().size();
			Z_t = vocab.size() - T_t;
			
			return T_t / (Z_t * (C_t + T_t));
		}
	}
	
	private static String[] viterbi(String[] observations,String[] stateGraph){
		int numOfWords = observations.length;
		int numOfTags = stateGraph.length;
		
		float[][] viterbi = new float[numOfTags][numOfWords];
		int[][] backpointers = new int[numOfTags][numOfWords];
		
		//Init
		for(int i=0;i<numOfTags;i++){
			
			float posTagTransitionProba = posTransitionsProba[posTagsToIndex.get("<s>")][i];
			float smoothedWordTag = writtenBellForWords(observations[0],stateGraph[i]);
			
			float value = (float)(Math
					.log(posTagTransitionProba) + Math.
					log(smoothedWordTag));
			viterbi[i][0] = value;
			
			backpointers[i][0] = 0;
			
			if(i == numOfTags-1){
				System.out.println("time to go out");
			}
		}
		
		//Recursion
		for(int t=1;t<numOfWords;t++){
			for(int s=0;s<numOfTags;s++){
				float max = -Float.MAX_VALUE, temp = 0;
				int back = 0;
				
				for(int sPrime=0;sPrime<numOfTags;sPrime++){
					temp = viterbi[sPrime][t - 1]
							+ (float) (Math.log(posTransitionsProba[sPrime][s]) + Math
									.log(writtenBellForWords(observations[t],
											stateGraph[s])));
					
					if(temp>max){
						max = temp;
						back = sPrime;
					}
				}
				
				viterbi[s][t] = max;
				backpointers[s][t] = back;
			}
		}
		
		//Termination
		float last;
		int lastpoint = 0;
		float max = -Float.MAX_VALUE;
		float temp = 0;
		for (int s = 0; s < numOfTags; s++) {
			temp = viterbi[s][numOfWords-1]
					+ (float) Math.log(posTransitionsProba[s][posTagsToIndex
							.get("</s>")]);
			if (temp > max) {
				max = temp;
				lastpoint = s;
			}
		}
		last = max;
		
		String[] predictedTags = new String[numOfWords];
		for (int t = numOfWords - 1; t >= 0; t--) {
			predictedTags[t] = stateGraph[lastpoint];
			lastpoint = backpointers[lastpoint][t];
		}

		return predictedTags;
	}
	
	private static String outputPrinter(String[] observations, String[] tags) {
		String output = "";
		for (int i = 0; i < observations.length; i++) {
			output += observations[i] + "/" + tags[i] + " ";
		}
		return output.trim() + "\n";
	}
	
	@SuppressWarnings("resource")
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		if (args.length < 3) {
			System.err.println("usage:\tjava run_tagger <sents.test> <model_file> <sents.out>");
			System.exit(1);
		}
		
		sents_test = args[0];
		model_file = args[1];
		sents_out = args[2];
		
		try{
			// Load model_file
			ObjectInputStream inputStream = null;
			inputStream = new ObjectInputStream(new FileInputStream(model_file));
			Object obj = null;
			obj = inputStream.readObject();
			Model model = null;
			if (obj instanceof Model) {
				model = (Model) obj;
				pennTreeTags = model.getPennTreeTags();
				pennTreeTagsPlus = model.getPennTreeTagsPlus();
				posTransitions = model.getPosTransitions();
				posTagsToIndex = model.getPosTagsToIndex();
				vocab = model.getVocab();
				posTagsToWordsAndCount = model.getPosTagsToWordsAndCount();
				posTransitionsProba = model.getPosTransitionsProba();
				tagCount = model.getTagCount();
				results = new int[pennTreeTags.length][pennTreeTags.length];
			}
			
			// Init results for Precision/Recall/F-score
			for (int i = 0; i < results.length; i++) {
				for (int j = 0; j < results[i].length; j++) {
					results[i][j] = 0;
				}
			}
			
			// Open sents.test file and run tagger on sentences
			FileInputStream fstream = new FileInputStream(sents_test);
			FileWriter outstream = new FileWriter(sents_out);
			BufferedWriter out = new BufferedWriter(outstream);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String tagged, untagged, prediction;
			String[] observations, correctTags, predictedTags;
			String c, p;
			boolean taggedTest = false;
			
			while((tagged = br.readLine()) != null){
				untagged = stripTrainingInstance(tagged);
				observations = untagged.split(" ");
				predictedTags = viterbi(observations, pennTreeTags);
				prediction = outputPrinter(observations, predictedTags);
				out.write(prediction);
			}
			
			out.close();
			System.out.println("finished");
		}catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
