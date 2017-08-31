import java.io.File;
import java.io.BufferedReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.List;
import java.util.ArrayList;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.meta.FilteredClassifier;

import weka.core.Instances;
import weka.core.Instance;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.converters.ArffSaver;
import weka.core.converters.ArffLoader.ArffReader;
import weka.core.tokenizers.NGramTokenizer;

import weka.filters.unsupervised.attribute.StringToWordVector;


/**
 * This class implements a Multinomial NaiveBayes text classifier using WEKA.
 * @author Alfred Francis - https://alfredfrancis.github.io
 * @see http://weka.wikispaces.com/Programmatic+Use
 */
public class WekaClassifier {

    private FilteredClassifier classifier;

    //declare train and test data Instances
    private Instances trainData;
    private Instances testData;

    //declare attributes of Instance
    private ArrayList < Attribute > wekaAttributes;

    //declare and initialize file locations
    private final String TRAIN_DATA = "dataset/train.txt";
    private final String TRAIN_ARFF_ARFF = "dataset/train.arff";
    private final String TEST_DATA = "dataset/test.txt";
    private final String TEST_DATA_ARFF = "dataset/test.arff";

    WekaClassifier() {

        /*
         * Class for running an arbitrary classifier on data that has been passed through an arbitrary filter
         * Training data and test instances will be processed by the filter without changing their structure
         */
        classifier = new FilteredClassifier();

        // set Multinomial NaiveBayes as arbitrary classifier
        classifier.setClassifier(new NaiveBayesMultinomial());

        // Declare text attribute to hold the message
        Attribute attribute_text = new Attribute("text", (List < String > ) null);

        // Declare the label attribute along with its values
        ArrayList < String > classAttributeValues = new ArrayList < String > ();
        classAttributeValues.add("spam");
        classAttributeValues.add("ham");
        Attribute classAttribute = new Attribute("label", classAttributeValues);

        // Declare the feature vector
        wekaAttributes = new ArrayList < Attribute > ();
        wekaAttributes.add(classAttribute);
        wekaAttributes.add(attribute_text);

    }

    /**
     * load training data and set feature generators
     */
    public void transform() throws Exception {


        trainData = loadRawDataset(TRAIN_DATA);
        saveArff(trainData, TRAIN_ARFF_ARFF);

        // create the filter and set the attribute to be transformed from text into a feature vector (the last one)
        StringToWordVector filter = new StringToWordVector();
        filter.setAttributeIndices("last");

        //add ngram tokenizer to filter with min and max length set to 1
        NGramTokenizer tokenizer = new NGramTokenizer();
        tokenizer.setNGramMinSize(1);
        tokenizer.setNGramMaxSize(1);
        //use word delimeter
        tokenizer.setDelimiters("\\W");
        filter.setTokenizer(tokenizer);

        //convert tokens to lowercase
        filter.setLowerCaseTokens(true);

        //add filter to classifier
        classifier.setFilter(filter);

    }

    /**
     * build the classifier with the Training data
     */
    public void fit() throws Exception {
        classifier.buildClassifier(trainData);
    }



    /**
     * classify a new message into spam or ham.
     * @param message to be classified.
     * @return a class label (spam or ham )
     */
    public String predict(String text) throws Exception {

        // create new Instance for prediction.
        DenseInstance newinstance = new DenseInstance(2);

        //weka demand a dataset to be set to new Instance
        Instances newDataset = new Instances("predictiondata", wekaAttributes, 1);
        newDataset.setClassIndex(0);

        newinstance.setDataset(newDataset);

        // text attribute value set to value to be predicted
        newinstance.setValue(wekaAttributes.get(1), text);

        // predict most likely class for the instance
        double pred = classifier.classifyInstance(newinstance);

        // get original label
        String label = newDataset.classAttribute().value((int) pred);
        return label;
    }

    /**
     * evaluate the classifier with the Test data
     * @return evaluation summary as string
     */
    public String evaluate() throws Exception {
        //load testdata

        if (new File(TEST_DATA_ARFF).exists()) {
            testData = loadArff(TEST_DATA_ARFF);
            testData.setClassIndex(0);
        } else {
            testData = loadRawDataset(TEST_DATA);
            saveArff(testData, TEST_DATA_ARFF);
        }

        Evaluation eval = new Evaluation(testData);
        eval.evaluateModel(classifier, testData);
        return eval.toSummaryString();
    }

    /**
     * This method loads the model to be used as classifier.
     * @param fileName The name of the file that stores the text.
     */
    public void loadModel(String fileName) {
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(fileName));
            Object tmp = in .readObject();
            classifier = (FilteredClassifier) tmp; in .close();
            System.out.println("Loaded model: " + fileName);
        } catch (Exception e) {
            // Given the cast, a ClassNotFoundException must be caught along with the IOException
            System.out.println("Problem found when reading: " + fileName);
        }
    }

    /**
     * This method saves the trained model into a file. This is done by
     * simple serialization of the classifier object.
     * @param fileName The name of the file that will store the trained model.
     */

    public void saveModel(String fileName) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileName));
            out.writeObject(classifier);
            out.close();
            System.out.println("Saved model: " + fileName);
        } catch (IOException e) {
            System.out.println("Problem found when writing: " + fileName);
        }
    }

    /**
     * Loads a dataset in space seperated text file and convert it to Arff format.
     * @param fileName The name of the file.
     */
    public Instances loadRawDataset(String filename) throws IOException {
        /* 
         *  Create an empty training set
         *  name the relation “Rel”.
         *  set intial capacity of 10*
         */
        Instances dataset = new Instances("SMS spam", wekaAttributes, 10);

        // Set class index
        dataset.setClassIndex(0);

        // read text file, parse data and add to instance
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            for (String line;
                (line = br.readLine()) != null;) {
                try {

                    // split at first occurance of n no. of words
                    String parts[] = line.split("\\s+", 2);

                    // basic validation
                    if (!parts[0].isEmpty() && !parts[1].isEmpty()) {

                        DenseInstance row = new DenseInstance(2);
                        row.setValue(wekaAttributes.get(0), parts[0]);
                        row.setValue(wekaAttributes.get(1), parts[1]);

                        // add row to instances
                        dataset.add(row);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("invalid row");
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return dataset;

    }

    /**
     * Loads a dataset in ARFF format. If the file does not exist, or
     * it has a wrong format, the attribute trainData is null.
     * @param fileName The name of the file that stores the dataset.
     */
    public Instances loadArff(String fileName) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            ArffReader arff = new ArffReader(reader);
            Instances dataset = arff.getData();
            System.out.println("loaded dataset: " + fileName);
            reader.close();
            return dataset;
        } catch (IOException e) {
            System.out.println("Problem found when reading: " + fileName);
            return null;
        }
    }

    /**
     * This method saves a dataset in ARFF format.
     * @param dataset dataset in arff format
     * @param fileName The name of the file that stores the dataset.
     */
    public void saveArff(Instances dataset, String filename) throws IOException {
        try {
            // initialize 
            ArffSaver arffSaverInstance = new ArffSaver();
            arffSaverInstance.setInstances(dataset);
            arffSaverInstance.setFile(new File(filename));
            arffSaverInstance.writeBatch();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method. With an example usage of this class.
     */
    public static void main(String[] args) throws Exception {

        final String MODEL = "models/sms.dat";

        WekaClassifier wt = new WekaClassifier();

        if (new File(MODEL).exists()) {
            wt.loadModel(MODEL);
        } else {
            wt.transform();
            wt.fit();
            wt.saveModel(MODEL);
        }

        //run few predictions
        System.out.println(wt.predict("how are you ?"));
        System.out.println(wt.predict("u have won the 1 lakh prize"));

        //run evaluation
        System.out.println(wt.evaluate());
    }
}