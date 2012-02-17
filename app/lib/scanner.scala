package afm


trait Scanner {
  def run: (Double, Double, Int)
}


object SingleFieldScanner extends Scanner {
  def run = new MongoStreamDetector(Model.sortOn).run
}


trait FeaturedScanner {
  def multiPass[A](featuresFile: String, sortedFeaturesFile: String, extractor: FeatureExtractor[A]) = {
    val feature = new MongoFeatureExtractor(extractor, featuresFile)
    feature.run

    val sorter = new Sorter(featuresFile, sortedFeaturesFile)
    sorter.run

    val lines = sorter.lines

    //val runner = new MongoStreamDetector("n", Some(lines))
    //val runner = new MongoExternallySorted(sortedFeaturesFile)
    val runner = new PrefetchingMongoExternallySorted(sortedFeaturesFile, Some(lines))
    //val runner = new ParalellFetchMongoExternallySorted(sortedFeaturesFile, Some(lines))
    //val runner = new CmdlineMongoExternallySorted(sortedFeaturesFile, Some(lines))
    runner.run
  }
}

object NgramScanner extends Scanner with FeaturedScanner {
  def run = {
    val features = new FieldFeatureExtractor(StringFieldDef("lastName", NullDistanceAlgo())) with NGramValueExtractor
    multiPass("/tmp/ngrams.txt", "/tmp/ngrams.sorted", features)
  }
}

object MergedSimhashScanner extends Scanner with FeaturedScanner {
  def run = {
    val features = new FieldFeatureExtractor(StringFieldDef("lastName", NullDistanceAlgo())) with RotatedSimhashValueExtractor
    multiPass("/tmp/simhash.txt", "/tmp/simhash.sorted", features)
  }
}

trait MultiPassScanner[A] extends Scanner {
  def extractorForIteration(i: Int): FeatureExtractor[A]

  def simhash(featuresFile: String, sortedFeaturesFile: String) = {
    var last = (0.0, 0.0, 0)

    val collector = new MongoDBCollector("candidates")
      
    for(i <- 0 until 8)  {
      val extractor = extractorForIteration(i)

      val feature = new MongoFeatureExtractor(extractor, featuresFile.format(i))
      feature.run
      
      val sorter = new Sorter(featuresFile.format(i), sortedFeaturesFile.format(i))
      sorter.run
      
      val lines = sorter.lines
      
      val runner = new PrefetchingMongoExternallySorted(sortedFeaturesFile.format(i), Some(lines), existingCollector=Some(collector))
      val (precision, recall, time) = runner.run
      last = (precision, recall, last._3 + time)
    }

    last 
  }

}

object MultiPassSimhashScanner extends Scanner with MultiPassScanner[String] {
  def run = simhash("/tmp/simhash-%s.txt", "/tmp/simhash-%s.sorted")

  def extractorForIteration(i: Int) = new FieldFeatureExtractor(StringFieldDef("lastName", NullDistanceAlgo())) with SimhashValueExtractor {
    def step = i
  }

}