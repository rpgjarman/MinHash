MinHash Java Implementation
===========================

This is a Java conversion of Chris McCormick's MinHash Python implementation.
The program demonstrates using the MinHash algorithm to search a large collection 
of documents to identify pairs of documents which have a lot of text in common.

This implementation is a direct translation from Python to Java, maintaining all
functionality including:
- Document shingling (3-word sequences)
- Direct Jaccard similarity calculation
- MinHash signature generation
- MinHash signature comparison
- Similar document pair detection

COMPILATION
-----------
To compile the program, use the Java compiler (javac):

    javac RunMinHashExample.java

This will create the RunMinHashExample.class file.

REQUIREMENTS
------------
- Java Development Kit (JDK) 8 or later
- The data files in the ./data/ directory:
  - articles_100.train and articles_100.truth
  - articles_1000.train and articles_1000.truth
  - articles_2500.train and articles_2500.truth
  - articles_10000.train and articles_10000.truth

RUNNING
-------
To run the program, use the Java runtime (java):

    java RunMinHashExample

The program will:
1. Parse the ground truth file from ./data/articles_1000.truth
2. Read the document data from ./data/articles_1000.train
3. Convert documents to sets of shingles (3-word sequences)
   - Each document is parsed into consecutive 3-word shingles
   - Shingles are hashed using CRC32 to 32-bit integer IDs
4. Calculate Jaccard similarities directly (for datasets <= 2500 documents)
   - Computes intersection and union of shingle sets for each document pair
   - This step is skipped for larger datasets as it becomes very slow
5. Generate MinHash signatures for all documents
   - Uses random hash functions of the form: h(x) = (a*x + b) % c
   - Creates signatures with 10 components (configurable via numHashes)
6. Compare all MinHash signatures
   - Counts matching components between signature pairs
   - Calculates estimated Jaccard similarity
7. Display document pairs with similarity above 0.5 threshold
   - Shows both estimated and actual Jaccard similarities
   - Reports true positives and false positives

CONFIGURATION
-------------
You can modify the following constants in RunMinHashExample.java:

- numHashes: Number of components in MinHash signatures (default: 10)
  - More components = more accurate but slower
  - Fewer components = faster but less accurate

- numDocs: Number of documents to process (default: 1000)
  - Available dataset sizes: 100, 1000, 2500, 10000
  - Note: Jaccard similarity calculation is only performed for numDocs <= 2500
    due to performance considerations (as in the original Python version)

- threshold: Similarity threshold for displaying pairs (default: 0.5)
  - Located in the "Display Similar Document Pairs" section

IMPLEMENTATION NOTES
--------------------
This Java implementation uses only standard Java libraries (no external dependencies):

- java.io.* for file I/O operations
- java.util.* for collections (HashMap, HashSet, ArrayList, etc.)
- java.util.zip.CRC32 for shingle hashing
- java.util.Random for random number generation

Key conversions from Python to Java:
- Python dict -> Java HashMap
- Python set -> Java HashSet  
- Python list -> Java ArrayList
- Python binascii.crc32() -> Java CRC32
- Python time.time() -> Java System.currentTimeMillis()
- Python random.randint() -> Java Random with bit masking

The algorithm follows the same logic as the original Python version:
1. Documents are represented as sets of shingle IDs (CRC32 hashes of 3-word sequences)
2. MinHash signatures are generated using random hash functions
3. Similarity is estimated by comparing signature components
4. Results are validated against actual Jaccard similarities

PERFORMANCE
-----------
As noted in the original implementation:
- For 10,000 documents, direct Jaccard calculation takes ~20 minutes
- MinHash approach takes ~3 minutes for the same dataset
- The MinHash method provides a fast approximation to Jaccard similarity

The program includes timing information for each major step to help analyze performance.

Run command below to create a zip folder to submit assignment
Compress-Archive -Path RunMinHashExample.java,RunMinHashExample.class,runMinHashExample.py,README.txt,report.tex,report.pdf,data -DestinationPath hw5.zip