// ======== RunMinHashExample =======
// This example code demonstrates comparing documents using the MinHash
// approach. 
//
// First, each document is represented by the set of shingles it contains. The
// documents can then be compared using the Jaccard similarity of their 
// shingle sets. This is computationally expensive, however, for large numbers
// of documents. 
//
// For comparison, we will also use the MinHash algorithm to calculate short 
// signature vectors to represent the documents. These MinHash signatures can 
// then be compared quickly by counting the number of components in which the 
// signatures agree. We'll compare all possible pairs of documents, and find 
// the pairs with high similarity.
//
// The program follows these steps:
// 1. Convert each test file into a set of shingles.
//    - The shingles are formed by combining three consecutive words together.
//    - Shingles are mapped to shingle IDs using the CRC32 hash.
// 2. Calculate all Jaccard similarities directly.
//    - This is ok for small dataset sizes. For the full 10,000 articles, it
//      takes 20 minutes!
// 3. Calculate the MinHash signature for each document.
//    - The MinHash algorithm is implemented using the random hash function 
//      trick which prevents us from having to explicitly compute random
//      permutations of all of the shingle IDs. For further explanation, see
//      section 3.3.5 of http://infolab.stanford.edu/~ullman/mmds/ch3.pdf
// 4. Compare all MinHash signatures to one another.
//    - Compare MinHash signatures by counting the number of components in which
//      the signatures are equal. Divide the number of matching components by
//      the signature length to get a similarity value.
//    - Display pairs of documents / signatures with similarity greater than a
//      threshold.

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

public class RunMinHashExample {
    
    // This is the number of components in the resulting MinHash signatures.
    // Correspondingly, it is also the number of random hash functions that
    // we will need in order to calculate the MinHash.
    private static final int numHashes = 10;
    
    // You can run this code for different portions of the dataset.
    // It ships with data set sizes 100, 1000, 2500, and 10000.
    private static final int numDocs = 1000;
    private static final String dataFile = "./data/articles_" + numDocs + ".train";
    private static final String truthFile = "./data/articles_" + numDocs + ".truth";
    
    // Record the maximum shingle ID that we assigned.
    private static final long maxShingleID = (1L << 32) - 1; // 2^32 - 1
    
    // We need the next largest prime number above 'maxShingleID'.
    // I looked this value up here: 
    // http://compoasso.free.fr/primelistweb/page/prime/liste_online_en.php
    private static final long nextPrime = 4294967311L;
    
    public static void main(String[] args) {
        // =============================================================================
        //                  Parse The Ground Truth Tables
        // =============================================================================
        // Build a dictionary mapping the document IDs to their plagiaries, and vice-
        // versa.
        Map<String, String> plagiaries = new HashMap<>();
        
        // Open the truth file.
        try (BufferedReader reader = new BufferedReader(new FileReader(truthFile))) {
            String line;
            // For each line of the files...
            while ((line = reader.readLine()) != null) {
                // Strip the newline character, if present.
                line = line.trim();
                
                String[] docs = line.split("\\s+");
                
                if (docs.length >= 2) {
                    // Map the two documents to each other.
                    plagiaries.put(docs[0], docs[1]);
                    plagiaries.put(docs[1], docs[0]);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading truth file: " + e.getMessage());
            return;
        }
        
        // =============================================================================
        //               Convert Documents To Sets of Shingles
        // =============================================================================
        
        System.out.println("Shingling articles...");
        
        // Create a dictionary of the articles, mapping the article identifier (e.g., 
        // "t8470") to the list of shingle IDs that appear in the document.
        Map<String, Set<Long>> docsAsShingleSets = new HashMap<>();
        
        List<String> docNames = new ArrayList<>();
        
        long t0 = System.currentTimeMillis();
        
        long totalShingles = 0;
        
        // Open the data file.
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            for (int i = 0; i < numDocs; i++) {
                // Read all of the words (they are all on one line) and split them by white
                // space.
                String line = reader.readLine();
                if (line == null) break;
                
                String[] words = line.split("\\s+");
                
                // Retrieve the article ID, which is the first word on the line.  
                String docID = words[0];
                
                // Maintain a list of all document IDs.  
                docNames.add(docID);
                
                // 'shinglesInDoc' will hold all of the unique shingle IDs present in the 
                // current document. If a shingle ID occurs multiple times in the document,
                // it will only appear once in the set (this is a property of Java sets).
                Set<Long> shinglesInDoc = new HashSet<>();
                
                // For each word in the document...
                // In Python: after del words[0], loop is range(0, len(words) - 2)
                // In Java: words[0] is docID, so we need to adjust indices
                // Python accesses words[index], words[index+1], words[index+2] after deletion
                // Java equivalent: words[index+1], words[index+2], words[index+3] (since words[0] is docID)
                // Loop condition: index < (words.length - 1) - 2 = words.length - 3
                for (int index = 0; index < words.length - 3; index++) {
                    // Construct the shingle text by combining three words together.
                    // words[0] is the docID, so actual words start at words[1]
                    String shingle = words[index + 1] + " " + words[index + 2] + " " + words[index + 3];
                    
                    // Hash the shingle to a 32-bit integer.
                    CRC32 crc = new CRC32();
                    crc.update(shingle.getBytes());
                    long crcValue = crc.getValue() & 0xFFFFFFFFL; // Ensure unsigned 32-bit
                    
                    // Add the hash value to the list of shingles for the current document. 
                    // Note that set objects will only add the value to the set if the set 
                    // doesn't already contain it. 
                    shinglesInDoc.add(crcValue);
                }
                
                // Store the completed list of shingles for this document in the dictionary.
                docsAsShingleSets.put(docID, shinglesInDoc);
                
                // Count the number of shingles across all documents.
                // In Python: totalShingles += (len(words) - 2) where words has docID removed
                // In Java: words.length includes docID, so (words.length - 1 - 2) = words.length - 3
                totalShingles += (words.length - 3);
            }
        } catch (IOException e) {
            System.err.println("Error reading data file: " + e.getMessage());
            return;
        }
        
        // Report how long shingling took.
        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        System.out.println("\nShingling " + numDocs + " docs took " + String.format("%.2f", elapsed) + " sec.");
        
        System.out.println("\nAverage shingles per doc: " + String.format("%.2f", (double) totalShingles / numDocs));
        
        // =============================================================================
        //                     Define Triangle Matrices
        // =============================================================================
        
        // Define virtual Triangle matrices to hold the similarity values. For storing
        // similarities between pairs, we only need roughly half the elements of a full
        // matrix. Using a triangle matrix requires less than half the memory of a full
        // matrix, and can protect the programmer from inadvertently accessing one of
        // the empty/invalid cells of a full matrix.
        
        // Calculate the number of elements needed in our triangle matrix
        int numElems = numDocs * (numDocs - 1) / 2;
        
        // Initialize two empty lists to store the similarity values. 
        // 'JSim' will be for the actual Jaccard Similarity values. 
        // 'estJSim' will be for the estimated Jaccard Similarities found by comparing
        // the MinHash signatures.
        double[] JSim = new double[numElems];
        double[] estJSim = new double[numElems];
        
        // =============================================================================
        //                 Calculate Jaccard Similarities
        // =============================================================================
        // In this section, we will directly calculate the Jaccard similarities by 
        // comparing the sets. This is included here to show how much slower it is than
        // the MinHash approach.
        
        // Calculating the Jaccard similarities gets really slow for large numbers
        // of documents.
        if (numDocs <= 2500) {
            System.out.println("\nCalculating Jaccard Similarities...");
            
            // Time the calculation.
            t0 = System.currentTimeMillis();
            
            // For every document pair...
            for (int i = 0; i < numDocs; i++) {
                // Print progress every 100 documents.
                if (i % 100 == 0) {
                    System.out.println("  (" + i + " / " + numDocs + ")");
                }
                
                // Retrieve the set of shingles for document i.
                Set<Long> s1 = docsAsShingleSets.get(docNames.get(i));
                
                for (int j = i + 1; j < numDocs; j++) {
                    // Retrieve the set of shingles for document j.
                    Set<Long> s2 = docsAsShingleSets.get(docNames.get(j));
                    
                    // Calculate and store the actual Jaccard similarity.
                    Set<Long> intersection = new HashSet<>(s1);
                    intersection.retainAll(s2);
                    
                    Set<Long> union = new HashSet<>(s1);
                    union.addAll(s2);
                    
                    JSim[getTriangleIndex(i, j, numDocs)] = (double) intersection.size() / union.size();
                }
            }
            
            // Calculate the elapsed time (in seconds)
            elapsed = (System.currentTimeMillis() - t0) / 1000.0;
            
            System.out.println("\nCalculating all Jaccard Similarities took " + String.format("%.2f", elapsed) + "sec");
        }
        
        // Delete the Jaccard Similarities, since it's a pretty big matrix.    
        JSim = null;
        System.gc(); // Suggest garbage collection
        
        // =============================================================================
        //                 Generate MinHash Signatures
        // =============================================================================
        
        // Time this step.
        t0 = System.currentTimeMillis();
        
        System.out.println("\nGenerating random hash functions...");
        
        // Our random hash function will take the form of:
        //   h(x) = (a*x + b) % c
        // Where 'x' is the input value, 'a' and 'b' are random coefficients, and 'c' is
        // a prime number just greater than maxShingleID.
        
        // Generate a list of 'k' random coefficients for the random hash functions,
        // while ensuring that the same value does not appear multiple times in the 
        // list.
        Random random = new Random();
        List<Long> coeffA = pickRandomCoeffs(numHashes, random);
        List<Long> coeffB = pickRandomCoeffs(numHashes, random);
        
        System.out.println("\nGenerating MinHash signatures for all documents...");
        
        // List of documents represented as signature vectors
        List<List<Long>> signatures = new ArrayList<>();
        
        // Rather than generating a random permutation of all possible shingles, 
        // we'll just hash the IDs of the shingles that are *actually in the document*,
        // then take the lowest resulting hash code value. This corresponds to the index 
        // of the first shingle that you would have encountered in the random order.
        
        // For each document...
        for (String docID : docNames) {
            // Get the shingle set for this document.
            Set<Long> shingleIDSet = docsAsShingleSets.get(docID);
            
            // The resulting minhash signature for this document. 
            List<Long> signature = new ArrayList<>();
            
            // For each of the random hash functions...
            for (int i = 0; i < numHashes; i++) {
                // For each of the shingles actually in the document, calculate its hash code
                // using hash function 'i'. 
                
                // Track the lowest hash ID seen. Initialize 'minHashCode' to be greater than
                // the maximum possible value output by the hash.
                long minHashCode = nextPrime + 1;
                
                // For each shingle in the document...
                for (Long shingleID : shingleIDSet) {
                    // Evaluate the hash function.
                    long hashCode = ((coeffA.get(i) * shingleID + coeffB.get(i)) % nextPrime);
                    
                    // Track the lowest hash code seen.
                    if (hashCode < minHashCode) {
                        minHashCode = hashCode;
                    }
                }
                
                // Add the smallest hash code value as component number 'i' of the signature.
                signature.add(minHashCode);
            }
            
            // Store the MinHash signature for this document.
            signatures.add(signature);
        }
        
        // Calculate the elapsed time (in seconds)
        elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        
        System.out.println("\nGenerating MinHash signatures took " + String.format("%.2f", elapsed) + "sec");
        
        // =============================================================================
        //                     Compare All Signatures
        // =============================================================================
        
        System.out.println("\nComparing all signatures...");
        
        // Time this step.
        t0 = System.currentTimeMillis();
        
        // For each of the test documents...
        for (int i = 0; i < numDocs; i++) {
            // Get the MinHash signature for document i.
            List<Long> signature1 = signatures.get(i);
            
            // For each of the other test documents...
            for (int j = i + 1; j < numDocs; j++) {
                // Get the MinHash signature for document j.
                List<Long> signature2 = signatures.get(j);
                
                int count = 0;
                // Count the number of positions in the minhash signature which are equal.
                for (int k = 0; k < numHashes; k++) {
                    if (signature1.get(k).equals(signature2.get(k))) {
                        count++;
                    }
                }
                
                // Record the percentage of positions which matched.    
                estJSim[getTriangleIndex(i, j, numDocs)] = (double) count / numHashes;
            }
        }
        
        // Calculate the elapsed time (in seconds)
        elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        
        System.out.println("\nComparing MinHash signatures took " + String.format("%.2f", elapsed) + "sec");
        
        // =============================================================================
        //                   Display Similar Document Pairs
        // =============================================================================
        
        // Count the true positives and false positives.
        int tp = 0;
        int fp = 0;
        
        double threshold = 0.5;
        System.out.println("\nList of Document Pairs with J(d1,d2) more than " + threshold);
        System.out.println("Values shown are the estimated Jaccard similarity and the actual");
        System.out.println("Jaccard similarity.\n");
        System.out.println("                   Est. J   Act. J");
        
        // For each of the document pairs...
        for (int i = 0; i < numDocs; i++) {
            for (int j = i + 1; j < numDocs; j++) {
                // Retrieve the estimated similarity value for this pair.
                double estJ = estJSim[getTriangleIndex(i, j, numDocs)];
                
                // If the similarity is above the threshold...
                if (estJ > threshold) {
                    // Calculate the actual Jaccard similarity for validation.
                    Set<Long> s1 = docsAsShingleSets.get(docNames.get(i));
                    Set<Long> s2 = docsAsShingleSets.get(docNames.get(j));
                    
                    Set<Long> intersection = new HashSet<>(s1);
                    intersection.retainAll(s2);
                    
                    Set<Long> union = new HashSet<>(s1);
                    union.addAll(s2);
                    
                    double J = (double) intersection.size() / union.size();
                    
                    // Print out the match and similarity values with pretty spacing.
                    System.out.printf("  %5s --> %5s   %.2f     %.2f%n", 
                                     docNames.get(i), docNames.get(j), estJ, J);
                    
                    // Check whether this is a true positive or false positive.
                    // We don't need to worry about counting the same true positive twice
                    // because we implemented the for-loops to only compare each pair once.
                    if (docNames.get(j).equals(plagiaries.get(docNames.get(i)))) {
                        tp++;
                    } else {
                        fp++;
                    }
                }
            }
        }
        
        // Display true positive and false positive counts.
        System.out.println();
        System.out.println("True positives:  " + tp + " / " + (plagiaries.size() / 2));
        System.out.println("False positives: " + fp);
    }
    
    // Define a function to map a 2D matrix coordinate into a 1D index.
    private static int getTriangleIndex(int i, int j, int numDocs) {
        // If i == j that's an error.
        if (i == j) {
            System.err.println("Can't access triangle matrix with i == j");
            System.exit(1);
        }
        // If j < i just swap the values.
        if (j < i) {
            int temp = i;
            i = j;
            j = temp;
        }
        
        // Calculate the index within the triangular array.
        // This fancy indexing scheme is taken from pg. 211 of:
        // http://infolab.stanford.edu/~ullman/mmds/ch6.pdf
        // But I adapted it for a 0-based index.
        // Note: The division by two should not truncate, it
        //       needs to be a float. 
        int k = (int) (i * (numDocs - (i + 1) / 2.0) + j - i) - 1;
        
        return k;
    }
    
    // Generate a list of 'k' random coefficients for the random hash functions,
    // while ensuring that the same value does not appear multiple times in the 
    // list.
    private static List<Long> pickRandomCoeffs(int k, Random random) {
        // Create a list of 'k' random values.
        List<Long> randList = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        
        while (k > 0) {
            // Get a random shingle ID in the range [0, maxShingleID].
            // Use nextInt with a bound to ensure we get a value in the right range.
            // Since maxShingleID is 2^32 - 1, we need to handle it carefully.
            long randIndex;
            do {
                // Generate a random long and mask it to get a value in [0, maxShingleID]
                randIndex = (random.nextLong() & 0xFFFFFFFFL) & maxShingleID;
            } while (used.contains(randIndex));
            
            // Add the random number to the list.
            randList.add(randIndex);
            used.add(randIndex);
            k--;
        }
        
        return randList;
    }
}

