import java.io.*;
import java.util.*;


public class build_tagger {
	/**
	 * @param args
	 */
	static String sents_train = "";
	static String sents_devt = "";
	static String model_file = "";
	static String[] pennTreeTags = { "CC", "CD", "DT", "EX", "FW", "IN", "JJ",
		"JJR", "JJS", "LS", "MD", "NN", "NNS", "NNP", "NNPS", "PDT", "POS",
		"PRP", "PRP$", "RB", "RBR", "RBS", "RP", "SYM", "TO", "UH", "VB",
		"VBD", "VBG", "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB", "$",
		"#", "``", "''", "-LRB-", "-RRB-", ",", ".", ":" };
	static String[] pennTreeTagsPlus = { "CC", "CD", "DT", "EX", "FW", "IN",
		"JJ", "JJR", "JJS", "LS", "MD", "NN", "NNS", "NNP", "NNPS", "PDT",
		"POS", "PRP", "PRP$", "RB", "RBR", "RBS", "RP", "SYM", "TO", "UH",
		"VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB",
		"$", "#", "``", "''", "-LRB-", "-RRB-", ",", ".", ":", "<s>",
		"</s>" };
	static int[][] posTransitions = new int[pennTreeTagsPlus.length][pennTreeTagsPlus.length];
	static Hashtable<String, Integer> posTagToIndex = new Hashtable<String, Integer>();
	static Hashtable<String, Hashtable<String,Integer>> posTagsToWordsAndCount = new Hashtable<String,Hashtable<String,Integer>>();
	static Hashtable<String,Integer> tagCount = new Hashtable<String,Integer>();
	static float[][] posTransitionsProba = new float[pennTreeTagsPlus.length][pennTreeTagsPlus.length];
	static Set<String> vocab = new HashSet<String>();

	public static float writtenBells(int fromTag, int toTag){
		if(posTransitions[fromTag][toTag]>0){
			float CK_minus_1_K = 0, CK_minus_1 = 0,TK_minus_1 = 0;
			
			for(int i=0;i<posTransitions[fromTag].length;i++){
				if(posTransitions[fromTag][i]>0){
					TK_minus_1++;
				}
				
				CK_minus_1 += posTransitions[fromTag][i];
			}
			
			CK_minus_1_K = posTransitions[fromTag][toTag];
			
			return CK_minus_1_K/(CK_minus_1 + TK_minus_1);
		}else{
			float CK_minus_1 = 0,TK_minus_1 = 0,ZK_minus_1 = 0;
			
			for(int i=0;i<posTransitions[fromTag].length;i++){
				if(posTransitions[fromTag][i]>0){
					TK_minus_1++;
				}else{
					ZK_minus_1++;
				}
				
				CK_minus_1 += posTransitions[fromTag][i];
			}

			return TK_minus_1/(ZK_minus_1 * (CK_minus_1 + TK_minus_1));
		}
	}
	
	public static void processInputs() throws IOException{
		FileInputStream fsstream = new FileInputStream(sents_train);
		DataInputStream dtstream = new DataInputStream(fsstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(dtstream));
		
		String strLine;
		String head,tail;
		String headWord, headTag, tailWord, tailTag;
		String[] tokens, temp;
		Hashtable<String, Integer> hashTemp;
		
		while((strLine = br.readLine()) != null){
			//Add start and end tag to the sentence
			strLine = "<s> " + strLine + " </s>";
			tokens = strLine.split(" ");
			
			//Start process for each word+tag combinations
			for(int i=0; i<tokens.length-1; i++){
				head = tokens[i];
				tail = tokens[i+1];
				
				if(head.equals("<s>")){
					headTag = head;
					headWord = "";
				}else{
					temp = head.split("/");
					headWord = "";
					for (int j = 0; j < temp.length - 1; j++) {
						headWord += temp[j];
					}
					
					vocab.add(headWord);
					headTag = temp[temp.length - 1];

				}
				
				if(tail.equals("</s>")){
					tailTag = tail;
					tailWord = "";
				}else{
					temp = tail.split("/");
					
					tailWord = "";
					for (int j = 0; j < temp.length - 1; j++) {
						tailWord += temp[j];
					}
					vocab.add(tailWord);
					tailTag = temp[temp.length - 1];
				}
				
				//now we are going to increment by 1 for the transitions counting
				posTransitions[posTagToIndex.get(headTag)][posTagToIndex.get(tailTag)] += 1;
				
				if(!headTag.equals("<s>")){
					
					//Check whetehr the word and count hashtable initialised
					if(!posTagsToWordsAndCount.containsKey(headTag)){
						hashTemp = new Hashtable<String,Integer>();
						posTagsToWordsAndCount.put(headTag, hashTemp);
					}
					
					hashTemp = posTagsToWordsAndCount.get(headTag);
					
					//Check whether the word is counted before. If not, make an initalization for it
					if(!hashTemp.containsKey(headWord)){
						hashTemp.put(headWord, 0);
					}
					
					hashTemp.put(headWord, hashTemp.get(headWord) + 1);
					posTagsToWordsAndCount.put(headTag, hashTemp);
				}
				
				//Then, we are going to do counting for the head tag
				if(!tagCount.containsKey(headTag)){
					tagCount.put(headTag,0);
				}
				
				tagCount.put(headTag, tagCount.get(headTag) + 1);
				
				//And, do not forget about the tailTag :P
				if(tailTag.equals("</s>")){
					if (!tagCount.containsKey(tailTag)) {
						tagCount.put(tailTag, 0);
					}
					tagCount.put(tailTag, tagCount.get(tailTag) + 1);
				}
				
			}
		}
		
		dtstream.close();
	}
	
	public static void smoothing(){
		float proba = 0;
		for (int i = 0; i < posTransitions.length; i++) {
			for (int j = 0; j < posTransitions[i].length; j++) {
				proba = writtenBells(i, j);
				if (proba != Float.NaN) {
					posTransitionsProba[i][j] = proba;
				} else {
					posTransitionsProba[i][j] = (float) -1;
				}
			}
		}
	}
	
	public static void saveModel(){
		// Saving extracted info into model
		ObjectOutputStream outputStream = null;
		try {
			outputStream = new ObjectOutputStream(new FileOutputStream(
					model_file));
			Model model = new Model(pennTreeTags, pennTreeTagsPlus,
					posTransitions, posTagToIndex, vocab,
					posTagsToWordsAndCount, posTransitionsProba, tagCount);
			outputStream.writeObject(model);
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			// Close the ObjectOutputStream
			try {
				if (outputStream != null) {
					outputStream.flush();
					outputStream.close();
					System.out.println("finished");
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args){
		if(args.length<3){
			System.err.println("Usage:\tjava build_tagger <sents.train> <sents.devt> <model_file>");
			System.exit(1);
		}
		
		//Init file pointers
		sents_train = args[0];
		sents_devt = args[1];
		model_file = args[2];
		
		//Init pos transitions
		for (int i = 0; i < posTransitions.length; i++) {
			for (int j = 0; j < posTransitions[i].length; j++) {
				posTransitions[i][j] = 0;
			}
		}
		
		//init , for fast retrieving the index of a particular tag
		for (int i = 0; i < pennTreeTagsPlus.length; i++) {
			posTagToIndex.put(pennTreeTagsPlus[i], i);
		}

		//Main execution of data processing
		try{
			//First, process the data we have
			processInputs();
			
			//Now let's start to do some smoothing!
			smoothing();
			
			//Well, now we are finishing it!
			saveModel();
			
		}catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
