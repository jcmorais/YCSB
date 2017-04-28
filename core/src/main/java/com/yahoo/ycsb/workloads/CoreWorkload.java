/**
 * Copyright (c) 2010 Yahoo! Inc., Copyright (c) 2016 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.yahoo.ycsb.workloads;

import com.yahoo.ycsb.*;
import com.yahoo.ycsb.generator.*;
import com.yahoo.ycsb.measurements.Measurements;

import java.io.IOException;
import java.util.*;

/**
 * The core benchmark scenario. Represents a set of clients doing simple CRUD operations. The
 * relative proportion of different kinds of operations, and other properties of the workload,
 * are controlled by parameters specified at runtime.
 * <p>
 * Properties to control the client:
 * <UL>
 * <LI><b>fieldcount</b>: the number of fields in a record (default: 10)
 * <LI><b>fieldlength</b>: the size of each field (default: 100)
 * <LI><b>readallfields</b>: should reads read all fields (true) or just one (false) (default: true)
 * <LI><b>writeallfields</b>: should updates and read/modify/writes update all fields (true) or just
 * one (false) (default: false)
 * <LI><b>readproportion</b>: what proportion of operations should be reads (default: 0.95)
 * <LI><b>updateproportion</b>: what proportion of operations should be updates (default: 0.05)
 * <LI><b>insertproportion</b>: what proportion of operations should be inserts (default: 0)
 * <LI><b>scanproportion</b>: what proportion of operations should be scans (default: 0)
 * <LI><b>readmodifywriteproportion</b>: what proportion of operations should be read a record,
 * modify it, write it back (default: 0)
 * <LI><b>requestdistribution</b>: what distribution should be used to select the records to operate
 * on - uniform, zipfian, hotspot, sequential, exponential or latest (default: uniform)
 * <LI><b>maxscanlength</b>: for scans, what is the maximum number of records to scan (default: 1000)
 * <LI><b>scanlengthdistribution</b>: for scans, what distribution should be used to choose the
 * number of records to scan, for each scan, between 1 and maxscanlength (default: uniform)
 * <LI><b>insertstart</b>: for parallel loads and runs, defines the starting record for this
 * YCSB instance (default: 0)
 * <LI><b>insertcount</b>: for parallel loads and runs, defines the number of records for this
 * YCSB instance (default: recordcount)
 * <LI><b>zeropadding</b>: for generating a record sequence compatible with string sort order by
 * 0 padding the record number. Controls the number of 0s to use for padding. (default: 1)
 * For example for row 5, with zeropadding=1 you get 'user5' key and with zeropading=8 you get
 * 'user00000005' key. In order to see its impact, zeropadding needs to be bigger than number of
 * digits in the record number.
 * <LI><b>insertorder</b>: should records be inserted in order by key ("ordered"), or in hashed
 * order ("hashed") (default: hashed)
 * </ul>
 */
public class CoreWorkload extends Workload {
  /**
   * The name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY = "table";

  /**
   * The default name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY_DEFAULT = "usertable";

  protected String table;

  /**
   * The name of the property for the number of fields in a record.
   */
  public static final String FIELD_COUNT_PROPERTY = "fieldcount";

  /**
   * Default number of fields in a record.
   */
  public static final String FIELD_COUNT_PROPERTY_DEFAULT = "10";

  protected int fieldcount;

  private List<String> fieldnames;

  /**
   * The name of the property for the field length distribution. Options are "uniform", "zipfian"
   * (favouring short records), "constant", and "histogram".
   * <p>
   * If "uniform", "zipfian" or "constant", the maximum field length will be that specified by the
   * fieldlength property. If "histogram", then the histogram will be read from the filename
   * specified in the "fieldlengthhistogram" property.
   */
  public static final String FIELD_LENGTH_DISTRIBUTION_PROPERTY = "fieldlengthdistribution";

  /**
   * The default field length distribution.
   */
  public static final String FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT = "constant";

  /**
   * The name of the property for the length of a field in bytes.
   */
  public static final String FIELD_LENGTH_PROPERTY = "fieldlength";

  /**
   * The default maximum length of a field in bytes.
   */
  public static final String FIELD_LENGTH_PROPERTY_DEFAULT = "100";

  int fieldlength;

  /**
   * The name of a property that specifies the filename containing the field length histogram (only
   * used if fieldlengthdistribution is "histogram").
   */
  public static final String FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY = "fieldlengthhistogram";

  /**
   * The default filename containing a field length histogram.
   */
  public static final String FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY_DEFAULT = "hist.txt";

  /**
   * Generator object that produces field lengths.  The value of this depends on the properties that
   * start with "FIELD_LENGTH_".
   */
  protected NumberGenerator fieldlengthgenerator;

  /**
   * The name of the property for deciding whether to read one field (false) or all fields (true) of
   * a record.
   */
  public static final String READ_ALL_FIELDS_PROPERTY = "readallfields";

  /**
   * The default value for the readallfields property.
   */
  public static final String READ_ALL_FIELDS_PROPERTY_DEFAULT = "true";

  protected boolean readallfields;

  /**
   * The name of the property for deciding whether to write one field (false) or all fields (true)
   * of a record.
   */
  public static final String WRITE_ALL_FIELDS_PROPERTY = "writeallfields";

  /**
   * The default value for the writeallfields property.
   */
  public static final String WRITE_ALL_FIELDS_PROPERTY_DEFAULT = "false";

  protected boolean writeallfields;

  /**
   * The name of the property for deciding whether to check all returned
   * data against the formation template to ensure data integrity.
   */
  public static final String DATA_INTEGRITY_PROPERTY = "dataintegrity";

  /**
   * The default value for the dataintegrity property.
   */
  public static final String DATA_INTEGRITY_PROPERTY_DEFAULT = "false";

  /**
   * Set to true if want to check correctness of reads. Must also
   * be set to true during loading phase to function.
   */
  private boolean dataintegrity;

  /**
   * The name of the property for the proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY = "readproportion";

  /**
   * The default proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY_DEFAULT = "0.95";

  /**
   * The name of the property for the proportion of transactions that are updates.
   */
  public static final String UPDATE_PROPORTION_PROPERTY = "updateproportion";

  /**
   * The default proportion of transactions that are updates.
   */
  public static final String UPDATE_PROPORTION_PROPERTY_DEFAULT = "0.05";

  /**
   * The name of the property for the proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY = "insertproportion";

  /**
   * The default proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY_DEFAULT = "0.0";

  /**
   * The name of the property for the proportion of transactions that are scans.
   */
  public static final String SCAN_PROPORTION_PROPERTY = "scanproportion";

  /**
   * The default proportion of transactions that are scans.
   */
  public static final String SCAN_PROPORTION_PROPERTY_DEFAULT = "0.0";

  /**
   * The name of the property for the proportion of transactions that are read-modify-write.
   */
  public static final String READMODIFYWRITE_PROPORTION_PROPERTY = "readmodifywriteproportion";

  /**
   * The default proportion of transactions that are scans.
   */
  public static final String READMODIFYWRITE_PROPORTION_PROPERTY_DEFAULT = "0.0";




  /**
   * The name of the property for the proportion of transactions that are scans.
   */
  public static final String MULTI_UPDATE_PROPORTION_PROPERTY="multiupdateproportion";
  public static final String COMPLEX_PROPORTION_PROPERTY="complexproportion";
  public static final String MULTI_READ_PROPORTION_PROPERTY="multireadproportion";
  public static final String SCAN_WRITE_PROPORTION_PROPERTY="scanwriteproportion";

  /**
   * The default proportion of transactions that are scans.
   */
  public static final String MULTI_UPDATE_PROPORTION_PROPERTY_DEFAULT="0.0";
  public static final String COMPLEX_PROPORTION_PROPERTY_DEFAULT="0.0";
  public static final String MULTI_READ_PROPORTION_PROPERTY_DEFAULT="0.0";
  public static final String SCAN_WRITE_PROPORTION_PROPERTY_DEFAULT="0.0";





  /**
   * The name of the property for the the distribution of requests across the keyspace. Options are
   * "uniform", "zipfian" and "latest"
   */
  public static final String REQUEST_DISTRIBUTION_PROPERTY = "requestdistribution";

  /**
   * The default distribution of requests across the keyspace.
   */
  public static final String REQUEST_DISTRIBUTION_PROPERTY_DEFAULT = "uniform";

  /**
   * The name of the property for adding zero padding to record numbers in order to match
   * string sort order. Controls the number of 0s to left pad with.
   */
  public static final String ZERO_PADDING_PROPERTY = "zeropadding";

  /**
   * The default zero padding value. Matches integer sort order
   */
  public static final String ZERO_PADDING_PROPERTY_DEFAULT = "1";


  /**
   * The name of the property for the max scan length (number of records).
   */
  public static final String MAX_SCAN_LENGTH_PROPERTY = "maxscanlength";

  /**
   * The default max scan length.
   */
  public static final String MAX_SCAN_LENGTH_PROPERTY_DEFAULT = "1000";

  /**
   * The name of the property for the scan length distribution. Options are "uniform" and "zipfian"
   * (favoring short scans)
   */
  public static final String SCAN_LENGTH_DISTRIBUTION_PROPERTY = "scanlengthdistribution";

  /**
   * The default max scan length.
   */
  public static final String SCAN_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT = "uniform";


  public static final String MAX_TRANSACTION_LENGTH_PROPERTY="maxtransactionlength";
  public static final String MAX_TRANSACTION_LENGTH_PROPERTY_DEFAULT="100";
  public static final String TRANSACTION_LENGTH_DISTRIBUTION_PROPERTY="transactionlengthdistribution";
  public static final String TRANSACTION_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT="uniform";

  /**
   * The name of the property for the order to insert records. Options are "ordered" or "hashed"
   */
  public static final String INSERT_ORDER_PROPERTY = "insertorder";

  /**
   * Default insert order.
   */
  public static final String INSERT_ORDER_PROPERTY_DEFAULT = "hashed";

  /**
   * Percentage data items that constitute the hot set.
   */
  public static final String HOTSPOT_DATA_FRACTION = "hotspotdatafraction";

  /**
   * Default value of the size of the hot set.
   */
  public static final String HOTSPOT_DATA_FRACTION_DEFAULT = "0.2";

  /**
   * Percentage operations that access the hot set.
   */
  public static final String HOTSPOT_OPN_FRACTION = "hotspotopnfraction";

  /**
   * Default value of the percentage operations accessing the hot set.
   */
  public static final String HOTSPOT_OPN_FRACTION_DEFAULT = "0.8";

  /**
   * How many times to retry when insertion of a single item to a DB fails.
   */
  public static final String INSERTION_RETRY_LIMIT = "core_workload_insertion_retry_limit";
  public static final String INSERTION_RETRY_LIMIT_DEFAULT = "0";

  /**
   * On average, how long to wait between the retries, in seconds.
   */
  public static final String INSERTION_RETRY_INTERVAL = "core_workload_insertion_retry_interval";
  public static final String INSERTION_RETRY_INTERVAL_DEFAULT = "3";

  protected NumberGenerator keysequence;
  protected DiscreteGenerator operationchooser;
  protected NumberGenerator keychooser;


  /**
   * The following is used for status oracle partitions in MegaOmid
   * We have two implementations, one is active at a time.
   * some variables are used only by one implementation.
   */

  /**
   * In MegaOmid, we generate a partitioned workload.
   * We need a keychooser for each partition
   * The following gives N random generator, one for each partition
   * Each random generator works on a seperate partition
   */
  NumberGenerator[] partitionedKeychoosers;

  /**
   * To squeeze more performance from HBase, we need a sequential workload.
   * In this case, instead of using partitionedKeychoosers, we use a pool of
   * sequentail integer generators
   * Each seqGenerator operates on the entire key range.
   * This is ok due to the sequential key generation:
   * they mostly stay in one partition.
   */
  Vector<SeqGenerator> keychoosersPool;

  /**
   * At the start, we assign a random partition to each thread.
   * The partition is selected by the following random generator
   */
  NumberGenerator partitionRandomSelector;

  /**
   * We randomly switch from local to global transactions
   * with a probability of globalchance (out of 100)
   * The following generates a random number for this purpose.
   */
  NumberGenerator globalTxnRndSelector;
  int globalchance;//chance of a global txn %

  /**
   * When we switch to global transactions, we use globalSeqGenerator to select
   * the next row id. It selects one of the seqGenerators from the pool and
   * get the next int from it.
   */
  NumberGenerator globalSeqGenerator;
  //end of MegaOmid variables


  NumberGenerator transactionlength;
  DiscreteGenerator complexchooser;



  protected NumberGenerator fieldchooser;
  protected AcknowledgedCounterGenerator transactioninsertkeysequence;
  protected NumberGenerator scanlength;
  protected boolean orderedinserts;
  protected int recordcount;
  protected int zeropadding;
  protected int insertionRetryLimit;
  protected int insertionRetryInterval;

  private Measurements measurements = Measurements.getMeasurements();

  protected static NumberGenerator getFieldLengthGenerator(Properties p) throws WorkloadException {
    NumberGenerator fieldlengthgenerator;
    String fieldlengthdistribution = p.getProperty(
        FIELD_LENGTH_DISTRIBUTION_PROPERTY, FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);
    int fieldlength =
        Integer.parseInt(p.getProperty(FIELD_LENGTH_PROPERTY, FIELD_LENGTH_PROPERTY_DEFAULT));
    String fieldlengthhistogram = p.getProperty(
        FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY, FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY_DEFAULT);
    if (fieldlengthdistribution.compareTo("constant") == 0) {
      fieldlengthgenerator = new ConstantIntegerGenerator(fieldlength);
    } else if (fieldlengthdistribution.compareTo("uniform") == 0) {
      fieldlengthgenerator = new UniformIntegerGenerator(1, fieldlength);
    } else if (fieldlengthdistribution.compareTo("zipfian") == 0) {
      fieldlengthgenerator = new ZipfianGenerator(1, fieldlength);
    } else if (fieldlengthdistribution.compareTo("histogram") == 0) {
      try {
        fieldlengthgenerator = new HistogramGenerator(fieldlengthhistogram);
      } catch (IOException e) {
        throw new WorkloadException(
            "Couldn't read field length histogram file: " + fieldlengthhistogram, e);
      }
    } else {
      throw new WorkloadException(
          "Unknown field length distribution \"" + fieldlengthdistribution + "\"");
    }
    return fieldlengthgenerator;
  }

  /**
   * Initialize the scenario.
   * Called once, in the main client thread, before any operations are started.
   */
  @Override
  public void init(Properties p) throws WorkloadException {
    table = p.getProperty(TABLENAME_PROPERTY, TABLENAME_PROPERTY_DEFAULT);

    fieldcount =
        Integer.parseInt(p.getProperty(FIELD_COUNT_PROPERTY, FIELD_COUNT_PROPERTY_DEFAULT));
    fieldlength=Integer.parseInt(p.getProperty(FIELD_LENGTH_PROPERTY,FIELD_LENGTH_PROPERTY_DEFAULT));
    fieldnames = new ArrayList<>();
    for (int i = 0; i < fieldcount; i++) {
      fieldnames.add("field" + i);
    }
    fieldlengthgenerator = CoreWorkload.getFieldLengthGenerator(p);

    recordcount =
        Integer.parseInt(p.getProperty(Client.RECORD_COUNT_PROPERTY, Client.DEFAULT_RECORD_COUNT));
    if (recordcount == 0) {
      recordcount = Integer.MAX_VALUE;
    }
    String requestdistrib =
        p.getProperty(REQUEST_DISTRIBUTION_PROPERTY, REQUEST_DISTRIBUTION_PROPERTY_DEFAULT);
    int maxscanlength =
        Integer.parseInt(p.getProperty(MAX_SCAN_LENGTH_PROPERTY, MAX_SCAN_LENGTH_PROPERTY_DEFAULT));
    String scanlengthdistrib =
        p.getProperty(SCAN_LENGTH_DISTRIBUTION_PROPERTY, SCAN_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);



    complexchooser = new DiscreteGenerator();
    complexchooser.addValue(0.5, "READ");
    complexchooser.addValue(0.5, "WRITE");
    int maxtransactionlength=Integer.parseInt(p.getProperty(MAX_TRANSACTION_LENGTH_PROPERTY,MAX_TRANSACTION_LENGTH_PROPERTY_DEFAULT));
    String transactionlengthdistrib=p.getProperty(TRANSACTION_LENGTH_DISTRIBUTION_PROPERTY,TRANSACTION_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);

    int insertstart =
        Integer.parseInt(p.getProperty(INSERT_START_PROPERTY, INSERT_START_PROPERTY_DEFAULT));
    int insertcount =
        Integer.parseInt(p.getProperty(INSERT_COUNT_PROPERTY, String.valueOf(recordcount - insertstart)));
    // Confirm valid values for insertstart and insertcount in relation to recordcount
    if (recordcount < (insertstart + insertcount)) {
      System.err.println("Invalid combination of insertstart, insertcount and recordcount.");
      System.err.println("recordcount must be bigger than insertstart + insertcount.");
      System.exit(-1);
    }
    zeropadding =
        Integer.parseInt(p.getProperty(ZERO_PADDING_PROPERTY, ZERO_PADDING_PROPERTY_DEFAULT));

    readallfields = Boolean.parseBoolean(
        p.getProperty(READ_ALL_FIELDS_PROPERTY, READ_ALL_FIELDS_PROPERTY_DEFAULT));
    writeallfields = Boolean.parseBoolean(
        p.getProperty(WRITE_ALL_FIELDS_PROPERTY, WRITE_ALL_FIELDS_PROPERTY_DEFAULT));

    dataintegrity = Boolean.parseBoolean(
        p.getProperty(DATA_INTEGRITY_PROPERTY, DATA_INTEGRITY_PROPERTY_DEFAULT));
    // Confirm that fieldlengthgenerator returns a constant if data
    // integrity check requested.
    if (dataintegrity && !(p.getProperty(
        FIELD_LENGTH_DISTRIBUTION_PROPERTY,
        FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT)).equals("constant")) {
      System.err.println("Must have constant field size to check data integrity.");
      System.exit(-1);
    }

    if (p.getProperty(INSERT_ORDER_PROPERTY, INSERT_ORDER_PROPERTY_DEFAULT).compareTo("hashed") == 0) {
      orderedinserts = false;
    } else if (requestdistrib.compareTo("exponential") == 0) {
      double percentile = Double.parseDouble(p.getProperty(
          ExponentialGenerator.EXPONENTIAL_PERCENTILE_PROPERTY,
          ExponentialGenerator.EXPONENTIAL_PERCENTILE_DEFAULT));
      double frac = Double.parseDouble(p.getProperty(
          ExponentialGenerator.EXPONENTIAL_FRAC_PROPERTY,
          ExponentialGenerator.EXPONENTIAL_FRAC_DEFAULT));
      keychooser = new ExponentialGenerator(percentile, recordcount * frac);
    } else {
      orderedinserts = true;
    }

    keysequence = new CounterGenerator(insertstart);
    operationchooser = createOperationGenerator(p);


    /**
     * read parameters related to global transactions in MegaOmid
     * also initialize the related variables
     */
    globalchance = Integer.parseInt(p.getProperty("globalchance","-1"));
    System.out.println("Global Txn Chance: " + globalchance + "%");
    globalTxnRndSelector = new UniformIntegerGenerator(0,100);//100% the total probability
    int partitions = Integer.parseInt(p.getProperty("partitions","1"));
    System.out.println("Number of partitions: " + partitions);
    partitionedKeychoosers = new NumberGenerator[partitions];
    partitionRandomSelector = new UniformIntegerGenerator(0,partitions-1);
    keychoosersPool = new Vector<SeqGenerator>();
    globalSeqGenerator = new GlobalSeqGenerator();
    int partitionSize = recordcount / partitions;
    //end of MegaOmid-specific variable initializations

    transactioninsertkeysequence = new AcknowledgedCounterGenerator(recordcount);
    if (requestdistrib.compareTo("uniform") == 0) {
      keychooser = new UniformIntegerGenerator(insertstart, insertstart + insertcount - 1);

      //initialize the key generators related to global transactions
      int end = 0;
      for (int i = 1; i <= partitions; i++) {
        int start = end;
        end = start + partitionSize;
        if (i == partitions)
          end = recordcount;
        partitionedKeychoosers[i-1] = new UniformIntegerGenerator(start,end-1);
        }


    } else if (requestdistrib.compareTo("sequential") == 0) {
      keychooser = new SequentialGenerator(insertstart, insertstart + insertcount - 1);


      

    } else if (requestdistrib.compareTo("zipfian") == 0) {
      // it does this by generating a random "next key" in part by taking the modulus over the
      // number of keys.
      // If the number of keys changes, this would shift the modulus, and we don't want that to
      // change which keys are popular so we'll actually construct the scrambled zipfian generator
      // with a keyspace that is larger than exists at the beginning of the test. that is, we'll predict
      // the number of inserts, and tell the scrambled zipfian generator the number of existing keys
      // plus the number of predicted keys as the total keyspace. then, if the generator picks a key
      // that hasn't been inserted yet, will just ignore it and pick another key. this way, the size of
      // the keyspace doesn't change from the perspective of the scrambled zipfian generator
      final double insertproportion = Double.parseDouble(
          p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));
      int opcount = Integer.parseInt(p.getProperty(Client.OPERATION_COUNT_PROPERTY));
      int expectednewkeys = (int) ((opcount) * insertproportion * 2.0); // 2 is fudge factor

      keychooser = new ScrambledZipfianGenerator(insertstart, insertstart + insertcount + expectednewkeys);


      //initialize the key generators related to global transactions
      int end = 0;
      for (int i = 1; i <= partitions; i++) {
        int start = end;
        end = start + partitionSize;
        if (i == partitions)
          end = recordcount + expectednewkeys;
        partitionedKeychoosers[i-1] = new ScrambledZipfianGenerator(start,end-1);
      }


    } else if (requestdistrib.compareTo("latest") == 0) {
      keychooser = new SkewedLatestGenerator(transactioninsertkeysequence);


      //initialize the key generators related to global transactions
      int end = 0;
      for (int i = 1; i <= partitions; i++) {
        int start = end;
        end = start + partitionSize;
        if (i == partitions)
          end = recordcount;
        //Here partitioning does not help since it is supposed to be around latest partition anyway
        partitionedKeychoosers[i-1] = new SkewedLatestGenerator(transactioninsertkeysequence);
      }


    } else if (requestdistrib.equals("hotspot")) {
      double hotsetfraction =
          Double.parseDouble(p.getProperty(HOTSPOT_DATA_FRACTION, HOTSPOT_DATA_FRACTION_DEFAULT));
      double hotopnfraction =
          Double.parseDouble(p.getProperty(HOTSPOT_OPN_FRACTION, HOTSPOT_OPN_FRACTION_DEFAULT));
      keychooser = new HotspotIntegerGenerator(insertstart, insertstart + insertcount - 1,
          hotsetfraction, hotopnfraction);

      //initialize the key generators related to global transactions
      int end = 0;
      for (int i = 1; i <= partitions; i++) {
        int start = end;
        end = start + partitionSize;
        if (i == partitions)
          end = recordcount;
        partitionedKeychoosers[i-1] = new HotspotIntegerGenerator(start,end-1,
            hotsetfraction, hotopnfraction);
      }

    } else {
      throw new WorkloadException("Unknown request distribution \"" + requestdistrib + "\"");
    }

    fieldchooser = new UniformIntegerGenerator(0, fieldcount - 1);

    if (scanlengthdistrib.compareTo("uniform") == 0) {
      scanlength = new UniformIntegerGenerator(1, maxscanlength);
    } else if (scanlengthdistrib.compareTo("zipfian") == 0) {
      scanlength = new ZipfianGenerator(1, maxscanlength);
    } else {
      throw new WorkloadException(
          "Distribution \"" + scanlengthdistrib + "\" not allowed for scan length");
    }

    if (transactionlengthdistrib.compareTo("uniform")==0)
    {
      transactionlength=new UniformIntegerGenerator(1,maxtransactionlength);
    }
    else if (transactionlengthdistrib.compareTo("zipfian")==0)
    {
      transactionlength=new ZipfianGenerator(1,maxtransactionlength);
    }
    else
    {
      throw new WorkloadException("Distribution \""+transactionlengthdistrib+"\" not allowed for transaction length");
    }

    insertionRetryLimit = Integer.parseInt(p.getProperty(
        INSERTION_RETRY_LIMIT, INSERTION_RETRY_LIMIT_DEFAULT));
    insertionRetryInterval = Integer.parseInt(p.getProperty(
        INSERTION_RETRY_INTERVAL, INSERTION_RETRY_INTERVAL_DEFAULT));
  }

  protected String buildKeyName(long keynum) {
    if (!orderedinserts) {
      keynum = Utils.hash(keynum);
    }
    String value = Long.toString(keynum);
    int fill = zeropadding - value.length();
    String prekey = "user";
    for (int i = 0; i < fill; i++) {
      prekey += '0';
    }
    return prekey + value;
  }

  /**
   * Builds a value for a randomly chosen field.
   */
  private HashMap<String, ByteIterator> buildSingleValue(String key) {
    HashMap<String, ByteIterator> value = new HashMap<>();

    String fieldkey = fieldnames.get(fieldchooser.nextValue().intValue());
    ByteIterator data;
    if (dataintegrity) {
      data = new StringByteIterator(buildDeterministicValue(key, fieldkey));
    } else {
      // fill with random data
      data = new RandomByteIterator(fieldlengthgenerator.nextValue().longValue());
    }
    value.put(fieldkey, data);

    return value;
  }

  /**
   * Builds values for all fields.
   */
  private HashMap<String, ByteIterator> buildValues(String key) {
    HashMap<String, ByteIterator> values = new HashMap<>();

    for (String fieldkey : fieldnames) {
      ByteIterator data;
      if (dataintegrity) {
        data = new StringByteIterator(buildDeterministicValue(key, fieldkey));
      } else {
        // fill with random data
        data = new RandomByteIterator(fieldlengthgenerator.nextValue().longValue());
      }
      values.put(fieldkey, data);
    }
    return values;
  }

  /**
   * Build a deterministic value given the key information.
   */
  private String buildDeterministicValue(String key, String fieldkey) {
    int size = fieldlengthgenerator.nextValue().intValue();
    StringBuilder sb = new StringBuilder(size);
    sb.append(key);
    sb.append(':');
    sb.append(fieldkey);
    while (sb.length() < size) {
      sb.append(':');
      sb.append(sb.toString().hashCode());
    }
    sb.setLength(size);

    return sb.toString();
  }

  /**
   * Do one insert operation. Because it will be called concurrently from multiple client threads,
   * this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
  public boolean doInsert(DB db, Object threadstate) {
    int keynum = keysequence.nextValue().intValue();
    //String dbkey = buildKeyName(keynum);
    String dbkey=makeKey(keynum);
    HashMap<String, ByteIterator> values = buildValues(dbkey);

    Status status;
    int numOfRetries = 0;
    do {
      status = db.insert(table, dbkey, values);
      if (null != status && status.isOk()) {
        break;
      }
      // Retry if configured. Without retrying, the load process will fail
      // even if one single insertion fails. User can optionally configure
      // an insertion retry limit (default is 0) to enable retry.
      if (++numOfRetries <= insertionRetryLimit) {
        System.err.println("Retrying insertion, retry count: " + numOfRetries);
        try {
          // Sleep for a random number between [0.8, 1.2)*insertionRetryInterval.
          int sleepTime = (int) (1000 * insertionRetryInterval * (0.8 + 0.4 * Math.random()));
          Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
          break;
        }

      } else {
        System.err.println("Error inserting, not retrying any more. number of attempts: " + numOfRetries +
            "Insertion Retry Limit: " + insertionRetryLimit);
        break;

      }
    } while (true);

    return null != status && status.isOk();
  }

  /**
   * Do one transaction operation. Because it will be called concurrently from multiple client
   * threads, this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
  public boolean doTransaction(DB db, Object threadState) {
    String operation = operationchooser.nextString();
    if(operation == null) {
      return false;
    }

    switch (operation) {
    case "READ":
      doTransactionRead(db, threadState);
      break;
    case "UPDATE":
      doTransactionUpdate(db, threadState);
      break;
    case "INSERT":
      doTransactionInsert(db, threadState);
      break;
    case "SCAN":
      doTransactionScan(db, threadState);
      break;

      case "MULTIUPDATE":
        doTransactionMultiUpdate(db, threadState);
        break;

      case "MULTIREAD":
        doTransactionMultiRead(db, threadState);
        break;

      case "COMPLEX":
        doTransactionComplex(db, threadState);
        break;

      case "SCANWRITE":
        doTransactionScanWrite(db, threadState);
        break;
    default:
      doTransactionReadModifyWrite(db, threadState);
    }


    return true;
  }


  //return a thread-specific state
  @Override
  public Object initThread(Properties p, int mythreadid, int threadcount) throws WorkloadException
  {
    ThreadWorkloadState state = new ThreadWorkloadState();
    state.partition = partitionRandomSelector.nextValue();
    return state;
  }

  class ThreadWorkloadState {
    Number intGeneratorIndex = -1;//use this to have a separate generator per each thread
    Number partition = -1;//the assigned partition to this client
    boolean lastTxnWasGlobal = false;//use it to rerun a global txn
    @Override
    public String toString() {
      return "ThreadWorkloadState: partition " + partition;
    }
  }

  String format = null;
  //get a key id and covert it to string
  String makeKey(int keynum) {
    //return "user"+keynum;
    if (format == null) {
      int digits = Integer.toString(recordcount).length();
      format = "user%0" + digits + "d";
    }
    return String.format(format,keynum);
  }

  //A class to generate sequentail row ids
  //It is used to get the highest throughput out of Hbase
  class SeqGenerator extends NumberGenerator {
    @Override
    public Number nextValue() {
      Number lastint = lastValue();
      lastint =  lastint.intValue()+1;
      if (lastint.intValue() == recordcount)//if it is wrapped around
        lastint = 0;
      setLastValue(lastint);
      return lastint;
    }

    @Override
    public double mean() {
      return 0;
    }
  }

  //This class generates global row ids but still it sticks to the sequential order history
  class GlobalSeqGenerator extends NumberGenerator {

    @Override
    public Number nextValue() {
      Number index = keychooser.nextValue();
      index = index.intValue() % keychoosersPool.size();
      //TODO: use a dedicated random generator
      SeqGenerator seqGenerator = keychoosersPool.elementAt(index.intValue());
      return seqGenerator.nextValue();
    }

    @Override
    public double mean() {
      return 0;
    }
  }



  NumberGenerator selectAKeyChooser(Object threadState) {
    ThreadWorkloadState tws = (ThreadWorkloadState)threadState;
    if (tws.intGeneratorIndex.intValue() == -1) {
      newSeqGenerator(tws);
    }
    NumberGenerator localIntGenerator = keychoosersPool.elementAt(tws.intGeneratorIndex.intValue());
    if (partitionedKeychoosers.length == 1) {//no need for partitioning
      return localIntGenerator;
    }
    if (tws.lastTxnWasGlobal) {//redo global txn
      tws.lastTxnWasGlobal = false;//reset it
      //System.out.println("GLOBAL");
      return globalSeqGenerator;
    }
    if (globalTxnRndSelector.nextValue().intValue() < globalchance) {
      tws.lastTxnWasGlobal = true;
      return globalSeqGenerator;
    }
    return localIntGenerator;
    //return keychooser;
  };

  synchronized void newSeqGenerator(ThreadWorkloadState tws) {
    SeqGenerator intGenerator = new SeqGenerator();
    Number randInt = keychooser.nextValue();
    intGenerator.setLastValue(randInt);
    int index = keychoosersPool.size();
    keychoosersPool.add(intGenerator);
    tws.intGeneratorIndex = index;
  }

  /**
   * Results are reported in the first three buckets of the histogram under
   * the label "VERIFY".
   * Bucket 0 means the expected data was returned.
   * Bucket 1 means incorrect data was returned.
   * Bucket 2 means null data was returned when some data was expected.
   */
  protected void verifyRow(String key, HashMap<String, ByteIterator> cells) {
    Status verifyStatus = Status.OK;
    long startTime = System.nanoTime();
    if (!cells.isEmpty()) {
      for (Map.Entry<String, ByteIterator> entry : cells.entrySet()) {
        if (!entry.getValue().toString().equals(buildDeterministicValue(key, entry.getKey()))) {
          verifyStatus = Status.UNEXPECTED_STATE;
          break;
        }
      }
    } else {
      // This assumes that null data is never valid
      verifyStatus = Status.ERROR;
    }
    long endTime = System.nanoTime();
    measurements.measure("VERIFY", (int) (endTime - startTime) / 1000);
    measurements.reportStatus("VERIFY", verifyStatus);
  }

  protected int nextKeynum() {
    int keynum;
    if (keychooser instanceof ExponentialGenerator) {
      do {
        keynum = transactioninsertkeysequence.lastValue() - keychooser.nextValue().intValue();
      } while (keynum < 0);
    } else {
      do {
        keynum = keychooser.nextValue().intValue();
      } while (keynum > transactioninsertkeysequence.lastValue());
    }
    return keynum;
  }

  public void doTransactionRead(DB db, Object threadState) {
    //to enable keys that are likely to be limited to a partition
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    // choose a random key
    int keynum=selectedKeychooser.nextValue().intValue();

    String keyname = makeKey(keynum);

    HashSet<String> fields = null;

    if (!readallfields) {
      // read a random field
      String fieldname = fieldnames.get(fieldchooser.nextValue().intValue());

      fields = new HashSet<String>();
      fields.add(fieldname);
    } else if (dataintegrity) {
      // pass the full field list if dataintegrity is on for verification
      fields = new HashSet<String>(fieldnames);
    }

    HashMap<String, ByteIterator> cells = new HashMap<String, ByteIterator>();
    db.read(table, keyname, fields, cells);

    if (dataintegrity) {
      verifyRow(keyname, cells);
    }
  }

  public void doTransactionReadModifyWrite(DB db, Object threadState) {
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    // choose a random key
    int keynum = selectedKeychooser.nextValue().intValue();

    String keyname = makeKey(keynum);

    HashSet<String> fields = null;

    if (!readallfields) {
      // read a random field
      String fieldname = fieldnames.get(fieldchooser.nextValue().intValue());

      fields = new HashSet<String>();
      fields.add(fieldname);
    }

    HashMap<String, ByteIterator> values;

    if (writeallfields) {
      // new data for all the fields
      values = buildValues(keyname);
    } else {
      // update a random field
      values = buildSingleValue(keyname);
    }

    // do the transaction

    HashMap<String, ByteIterator> cells = new HashMap<String, ByteIterator>();


    long ist = measurements.getIntendedtartTimeNs();
    long st = System.nanoTime();
    db.read(table, keyname, fields, cells);

    db.update(table, keyname, values);

    long en = System.nanoTime();

    if (dataintegrity) {
      verifyRow(keyname, cells);
    }

    measurements.measure("READ-MODIFY-WRITE", (int) ((en - st) / 1000));
    measurements.measureIntended("READ-MODIFY-WRITE", (int) ((en - ist) / 1000));
  }

  public void doTransactionScanWrite(DB db, Object threadState) {
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    // choose a random key
    int keynum = selectedKeychooser.nextValue().intValue();

    String startkeyname = makeKey(keynum);

    // choose a random scan length
    int len = scanlength.nextValue().intValue();

    HashSet<String> fields = null;
    HashMap<String,String> values=new HashMap<String,String>();

    if (!readallfields)
    {
      //read a random field
      String fieldname="field"+fieldchooser.nextString();

      fields=new HashSet<String>();
      fields.add(fieldname);

      String data=Utils.ASCIIString(fieldlength);
      values.put(fieldname,data);
    } else {
      //new data for all the fields
      for (int i=0; i<fieldcount; i++)
      {
        String fieldname="field"+i;
        String data=Utils.ASCIIString(fieldlength);
        values.put(fieldname,data);
      }
    }

    db.scanWrite(table,startkeyname,len,fields,values);
  }

  public void doTransactionScan(DB db, Object threadState) {
    //to enable keys that are likely to be limited to a partition
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);
    //choose a random key
    int keynum;
    do
    {
      keynum=selectedKeychooser.nextValue().intValue();
      //System.out.println("KEY: " + keynum + " " + threadState);
    }
    while (keynum>transactioninsertkeysequence.lastValue().intValue());

    if (!orderedinserts)
    {
      keynum=(int)Utils.hash(keynum);
    }
    //String startkeyname="user"+keynum;
    String startkeyname=makeKey(keynum);

    // choose a random scan length
    int len = scanlength.nextValue().intValue();

    HashSet<String> fields = null;

    if (!readallfields) {
      // read a random field
      String fieldname = fieldnames.get(fieldchooser.nextValue().intValue());

      fields = new HashSet<String>();
      fields.add(fieldname);
    }

    db.scan(table, startkeyname, len, fields, new Vector<HashMap<String, ByteIterator>>());
  }

  public void doTransactionUpdate(DB db, Object threadState) {
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    // choose a random key
    int keynum = selectedKeychooser.nextValue().intValue();

    String keyname =makeKey(keynum);

    HashMap<String, ByteIterator> values;

    if (writeallfields) {
      // new data for all the fields
      values = buildValues(keyname);
    } else {
      // update a random field
      values = buildSingleValue(keyname);
    }

    db.update(table, keyname, values);
  }



  public void doTransactionMultiRead(DB db, Object threadState)
  {
    //choose a random scan length
    int len=transactionlength.nextValue().intValue();

    //to enable keys that are likely to be limited to a partition
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    List<String> keys = new ArrayList<String>(len);
    for (int i = 0; i < len; i++) {
      //choose a random key
      int keynum;
      do
      {
        keynum=selectedKeychooser.nextValue().intValue();
        System.out.println("KEY: " + keynum + " " + threadState);
      }
      while (keynum>transactioninsertkeysequence.lastValue());

      if (!orderedinserts)
      {
        //keynum=Utils.hash(keynum);
      }
      //String keyname="user"+keynum;
      String keyname=makeKey(keynum);
      System.out.println("KEYNAME: " + keyname);
      keys.add(keyname);
    }

    HashSet<String> fields=null;

    if (!readallfields)
    {
      //read a random field
      String fieldname="field"+fieldchooser.nextString();

      fields=new HashSet<String>();
      fields.add(fieldname);
    }

    db.readMulti(table,keys,fields,new HashMap<String,Map<String,String>>());
  }

  public void doTransactionComplex(DB db, Object threadState)
  {
    //choose a random scan length
    int len=transactionlength.nextValue().intValue();
    //to enable keys that are likely to be limited to a partition
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    List<String> readKeys = new ArrayList<String>();
    List<String> writeKeys = new ArrayList<String>();
    for (int i = 0; i < len; i++) {
      //choose a random key
      int keynum;
      do
      {
        keynum=selectedKeychooser.nextValue().intValue();
        //System.out.println("KEY: " + keynum + " " + threadState);
      }
      while (keynum>transactioninsertkeysequence.lastValue());

      if (!orderedinserts)
      {
        keynum=(int)Utils.hash(keynum);
      }
      if (complexchooser.nextString().compareTo("READ") == 0) {
        //readKeys.add("user"+keynum);
        readKeys.add(makeKey(keynum));
      } else {
        //writeKeys.add("user"+keynum);
        writeKeys.add(makeKey(keynum));
      }
    }

    HashSet<String> fields=null;

    if (!readallfields)
    {
      //read a random field
      String fieldname="field"+fieldchooser.nextString();

      fields=new HashSet<String>();
      fields.add(fieldname);
    }

    HashMap<String,String> values=new HashMap<String,String>();

    if (writeallfields)
    {
      //new data for all the fields
      for (int i=0; i<fieldcount; i++)
      {
        String fieldname="field"+i;
        String data=Utils.ASCIIString(fieldlength);
        values.put(fieldname,data);
      }
    }
    else
    {
      //update a random field
      String fieldname="field"+fieldchooser.nextString();
      String data=Utils.ASCIIString(fieldlength);
      values.put(fieldname,data);
    }

    db.complex(table,readKeys,fields, new HashMap<String,Map<String,String>>(), writeKeys, values);
  }
  public void doTransactionMultiUpdate(DB db, Object threadState)
  {
    //choose a random scan length
    int len=transactionlength.nextValue().intValue();
    //to enable keys that are likely to be limited to a partition
    NumberGenerator selectedKeychooser = selectAKeyChooser(threadState);

    List<String> keys = new ArrayList<String>(len);
    for (int i = 0; i < len; i++) {
      //choose a random key
      int keynum;
      do
      {
        keynum=selectedKeychooser.nextValue().intValue();
        //System.out.println("KEY: " + keynum + " " + threadState);
      }
      while (keynum>transactioninsertkeysequence.lastValue());

      if (!orderedinserts)
      {
        keynum=(int)Utils.hash(keynum);
      }
      //keys.add("user"+keynum);
      keys.add(makeKey(keynum));
    }

    HashMap<String,String> values=new HashMap<String,String>();

    if (writeallfields)
    {
      //new data for all the fields
      for (int i=0; i<fieldcount; i++)
      {
        String fieldname="field"+i;
        String data=Utils.ASCIIString(fieldlength);
        values.put(fieldname,data);
      }
    }
    else
    {
      //update a random field
      String fieldname="field"+fieldchooser.nextString();
      String data=Utils.ASCIIString(fieldlength);
      values.put(fieldname,data);
    }

    db.updateMulti(table,keys,values);
  }

  public void doTransactionInsert(DB db, Object threadState) {
    // choose the next key
    int keynum = transactioninsertkeysequence.nextValue();

    try {
      String dbkey = makeKey(keynum);

      HashMap<String, ByteIterator> values = buildValues(dbkey);
      db.insert(table, dbkey, values);
    } finally {
      transactioninsertkeysequence.acknowledge(keynum);
    }
  }

  /**
   * Creates a weighted discrete values with database operations for a workload to perform.
   * Weights/proportions are read from the properties list and defaults are used
   * when values are not configured.
   * Current operations are "READ", "UPDATE", "INSERT", "SCAN" and "READMODIFYWRITE".
   *
   * @param p The properties list to pull weights from.
   * @return A generator that can be used to determine the next operation to perform.
   * @throws IllegalArgumentException if the properties object was null.
   */
  protected static DiscreteGenerator createOperationGenerator(final Properties p) {
    if (p == null) {
      throw new IllegalArgumentException("Properties object cannot be null");
    }
    final double readproportion = Double.parseDouble(
        p.getProperty(READ_PROPORTION_PROPERTY, READ_PROPORTION_PROPERTY_DEFAULT));
    final double updateproportion = Double.parseDouble(
        p.getProperty(UPDATE_PROPORTION_PROPERTY, UPDATE_PROPORTION_PROPERTY_DEFAULT));
    final double insertproportion = Double.parseDouble(
        p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));
    final double scanproportion = Double.parseDouble(
        p.getProperty(SCAN_PROPORTION_PROPERTY, SCAN_PROPORTION_PROPERTY_DEFAULT));
    final double readmodifywriteproportion = Double.parseDouble(p.getProperty(
        READMODIFYWRITE_PROPORTION_PROPERTY, READMODIFYWRITE_PROPORTION_PROPERTY_DEFAULT));


    double multiupdateproportion=Double.parseDouble(p.getProperty(MULTI_UPDATE_PROPORTION_PROPERTY,MULTI_UPDATE_PROPORTION_PROPERTY_DEFAULT));
    double multireadproportion=Double.parseDouble(p.getProperty(MULTI_READ_PROPORTION_PROPERTY,MULTI_READ_PROPORTION_PROPERTY_DEFAULT));
    double scanwriteproportion=Double.parseDouble(p.getProperty(SCAN_WRITE_PROPORTION_PROPERTY,SCAN_WRITE_PROPORTION_PROPERTY_DEFAULT));
    double complexproportion=Double.parseDouble(p.getProperty(COMPLEX_PROPORTION_PROPERTY,COMPLEX_PROPORTION_PROPERTY_DEFAULT));


    final DiscreteGenerator operationchooser = new DiscreteGenerator();
    if (readproportion > 0) {
      operationchooser.addValue(readproportion, "READ");
    }

    if (updateproportion > 0) {
      operationchooser.addValue(updateproportion, "UPDATE");
    }

    if (insertproportion > 0) {
      operationchooser.addValue(insertproportion, "INSERT");
    }

    if (scanproportion > 0) {
      operationchooser.addValue(scanproportion, "SCAN");
    }

    if (readmodifywriteproportion > 0) {
      operationchooser.addValue(readmodifywriteproportion, "READMODIFYWRITE");
    }

    if (multiupdateproportion>0)
    {
      operationchooser.addValue(multiupdateproportion,"MULTIUPDATE");
    }

    if (complexproportion>0)
    {
      operationchooser.addValue(complexproportion,"COMPLEX");
    }

    if (multireadproportion>0)
    {
      operationchooser.addValue(multireadproportion,"MULTIREAD");
    }

    if (scanwriteproportion>0)
    {
      operationchooser.addValue(scanwriteproportion,"SCANWRITE");
    }




    return operationchooser;
  }
}
