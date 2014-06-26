package scripts

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

@Grab(group='org.apache.lucene', module='lucene-core', version='4.5.1')

indexDirectory = FSDirectory.open(index_dir)

indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(Version.LUCENE_CURRENT, new StandardAnalyzer(Version.LUCENE_CURRENT)))

