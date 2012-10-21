package hu.unideb.inf.rdfizers.rpm;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import java.net.URL;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.codec.binary.Base64;

import com.hp.hpl.jena.rdf.model.Alt;
import com.hp.hpl.jena.rdf.model.Bag;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Seq;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

/**
 * Class to build <a href="http://jena.apache.org/">Apache Jena</a> RDF models from RPM package files.
 *
 * @see <a href="http://rpm5.org/docs/rpm-guide.html">RPM Guide</a>
 */
public class ModelBuilder {

	private static Logger	logger = LoggerFactory.getLogger(ModelBuilder.class);

	private static final String	FOAF_NS = "http://xmlns.com/foaf/0.1/";
	private static final String	RPM_NS = "http://purl.org/net/vocabulary/rpm#";

	private static Pattern	pattern = Pattern.compile("^(.*)<([\\p{Alnum}._%+-]+@[\\p{Alnum}.-]+\\.[\\p{Alpha}]{2,4})>$");

	private DataInputStream	in;
	private String	uri;
	private int	bytesRead = 0;

	private Model	model = ModelFactory.createDefaultModel();
	{
		model.setNsPrefix("xsd", XSD.getURI());
		model.setNsPrefix("rpm", RPM_NS);
		model.setNsPrefix("dc", DC.getURI());
		model.setNsPrefix("dcterms", DCTerms.getURI());
		model.setNsPrefix("foaf", FOAF_NS);
	}

	private Resource	rpmResource;

	private ModelBuilder(InputStream is, String uri) {
		in = new DataInputStream(is);
		this.uri = uri;
	}

	/**
	 * Consumes the RPM lead.
	 */
	private void processLead() throws IOException {
		if (in.readInt() != 0xedabeedb) throw new IOException("Not an RPM file");

		final int	majorVersion = in.readUnsignedByte();
		final int	minorVersion = in.readUnsignedByte();
		logger.debug("Version: {}.{}", majorVersion, minorVersion);

		final short	type = in.readShort();
		if (type != 0 && type != 1) throw new IOException("Invalid RPM lead");
		logger.debug("Type: {}", (type == 0 ? "binary" : "source"));

		final short	archNum = in.readShort();
		logger.debug("Architecture: {}", archNum);

		in.skipBytes(66);

		final short	osNum = in.readShort();
		logger.debug("OS: {}", osNum);

		final short	signatureType = in.readShort();
		logger.debug("Signature type: {}", signatureType);

		in.skipBytes(16);

		bytesRead += 96;
	}

	/**
	 * Utility class to store an RPM index entry.
	 */
	private static class IndexEntry {

		public int	tag;
		public int	type;
		public int	offset;
		public int	count;

		public IndexEntry(int tag, int type, int offset, int count) {
			this.tag = tag;
			this.type = type;
			this.offset = offset;
			this.count = count;
		}

		public String toString() {
			return String.format("[tag=%d type=%d offset=%d count=%d]", tag, type, offset, count);
		}

	}

	/**
	 * Processes an RPM header structure (signature or header).
	 */
	private Map processHeaderStructure(HeaderStructureType type) throws IOException {
		if (bytesRead % 8 != 0) {	// header structure must be aligned 8 byte boundary
			in.skipBytes(8 - (bytesRead % 8));
			bytesRead += 8 - (bytesRead % 8);
		}

		if (in.readUnsignedByte() != 0x8e
			|| in.readUnsignedByte() != 0xad
			|| in.readUnsignedByte() != 0xe8) throw new IOException("Invalid RPM header structure");

		in.skipBytes(5);	bytesRead += 8;
		final int	num = in.readInt();	bytesRead += 4;
		logger.debug("Number of index entries: {}", num);

		final int	size = in.readInt();	bytesRead += 4;
		logger.debug("Store size: {}", size);

		// read index entries
		IndexEntry[]	index = new IndexEntry[num];	
		for (int i = 0; i < num; ++i) {
			index[i] = new IndexEntry(
				in.readInt(),
				in.readInt(),
				in.readInt(),
				in.readInt()
			);	bytesRead += 16;
		}	

		// read store		
		byte[]	store = new byte[size];
		in.readFully(store);	bytesRead += size;

		switch(type) {
		case SIGNATURE:
			{
				Map<SignatureTag, Object>	map = new EnumMap<SignatureTag, Object>(SignatureTag.class);
				for (int i = 0; i < index.length; ++i) {
					SignatureTag	tag = SignatureTag.get(index[i].tag);
					if (tag != null) {
						if (HeaderType.get(index[i].type) != tag.getType()) throw new IOException("Invalid RPM signature");
						Object	value = getValueFromStore(index[i], store);
						map.put(tag, value);
					} else
						logger.warn("Skipping tag in signature: {}", index[i].tag);
				}
				return map;
			}
		case HEADER:
			{
				Map	map = new EnumMap<HeaderTag, Object>(HeaderTag.class);
				for (int i = 0; i < index.length; ++i) {
					HeaderTag	tag = HeaderTag.get(index[i].tag);
					if (tag != null) {
						if (HeaderType.get(index[i].type) != tag.getType()) {
							throw new IOException("Invalid RPM header");
						}
						Object	value = getValueFromStore(index[i], store);
						map.put(tag, value);
					} else
						logger.warn("Skipping tag in header: {}", index[i].tag);
				}
				return map;
			}
		}
		return null;
	}

	/**
	 * Gets an INT16 value from the store.
	 */
	private short getShort(byte[] store, int offset) {
		int	b1 = 0xff & store[offset];
		int	b2 = 0xff & store[offset + 1];
		int	intValue = (b1 << 8) + (b2 << 0);
		return (short) intValue;
	}

	/**
	 * Gets an INT32 value from the store.
	 */
	private int getInt(byte[] store, int offset) {
		int	b1 = 0xff & store[offset];
		int	b2 = 0xff & store[offset + 1];
		int	b3 = 0xff & store[offset + 2];
		int	b4 = 0xff & store[offset + 3];
		int	intValue = (b1 << 24) + (b2 << 16) + (b3 << 8) + (b4 << 0);
		return intValue;
	}

	/**
	 * Gets the value from the store that belongs to the entry.
	 */
	private Object getValueFromStore(IndexEntry entry, byte[] store) throws UnsupportedEncodingException {
		switch (HeaderType.get(entry.type)) {
		case NULL:
			{
				return null;
			}
		case CHAR:
			{
				// We can simply omit this type because it is not used currently (none of the documented signature tags or header tags uses this type)
				break;
			}
		case INT8:
			{
				byte[]	byteArray = new byte[entry.count];
				System.arraycopy(store, entry.offset, byteArray, 0, entry.count);
				return byteArray;
			}
		case INT16:
			{
				short[]	shortArray = new short[entry.count];
				for (int i = 0, j = entry.offset; i < entry.count; ++i, j += 2) {
					shortArray[i] = getShort(store, j);
				}
				return shortArray;
			}
		case INT32:
			{
				int[]	intArray = new int[entry.count];
				for (int i = 0, j = entry.offset; i < entry.count; ++i, j += 4) {
					intArray[i] = getInt(store, j);
				}
				return intArray;
			}
		case INT64:
			{
				// We can simply omit this type because it is not used (not supported)
				return null;
			}
		case STRING:
			{
				// UTF-8 character encoding is assumed
				int	i;
				for (i = entry.offset; store[i] != 0; ++i);
				return new String(store, entry.offset, i - entry.offset, "UTF-8");
			}
		case BIN:
			{
				byte[]	byteArray = new byte[entry.count];
				System.arraycopy(store, entry.offset, byteArray, 0, entry.count);
				return byteArray;
			}
		case STRING_ARRAY:
			{
				// UTF-8 character encoding is assumed
				String[]	stringArray = new String[entry.count];
				int	i = entry.offset - 1;
				int	start;
				for (int j = 0; j < entry.count; ++j) {
					for (start = ++i; store[i] != 0; ++i);
					stringArray[j] = new String(store, start, i - start, "UTF-8");
				}
				return stringArray;
			}
		case I18NSTRING:
			{
				// TODO: the use of this type needs clarification, until then treat it as an array of UTF-8 encoded strings
				String[]	stringArray = new String[entry.count];
				int	i = entry.offset - 1;
				int	start;
				for (int j = 0; j < entry.count; ++j) {
					for (start = ++i; store[i] != 0; ++i);
					stringArray[j] = new String(store, start, i - start, "UTF-8");
				}
				return stringArray;
			}
		}
		return null;
	}

	private void process() throws IOException {
		processLead();
		Map	signature = processHeaderStructure(HeaderStructureType.SIGNATURE);
		Map	header = processHeaderStructure(HeaderStructureType.HEADER);
		process(signature, header);
	}

	/**
	 * Processes the specified RPM package file.
	 *
	 * @param is the stream from which the RPM package file is to be read
	 * @param uri the URI of the RPM package file
	 * @throws IOException if an I/O error occurs
	 * @return an RDF model that stores metadata obtained from the RPM package file
	 */
	public static Model process(InputStream is, String uri) throws IOException {
		ModelBuilder	main = new ModelBuilder(is, uri);
		main.process();
		return main.model;
	}

	/**
	 * Processes the specified RPM package file.
	 *
	 * @param file the RPM package file
	 * @throws IOException if an I/O error occurs
	 * @return an RDF model that stores metadata obtained from the RPM package file
	 */
	public static Model process(File file) throws IOException {
		return process(new FileInputStream(file), file.toURI().toURL().toString());
	}

	/**
	 * Processes the specified RPM package file.
	 *
	 * @param fileName filename of the RPM package file
	 * @throws IOException if an I/O error occurs
	 * @return an RDF model that stores metadata obtained from the RPM package file
	 */
	public static Model process(String fileName) throws IOException {
		return process(new File(fileName));
	}

	/**
	 * Processes the specified RPM package file.
	 *
	 * @param url URL of the RPM package file
	 * @throws IOException if an I/O error occurs
	 * @return an RDF model that stores metadata obtained from the RPM package file
	 */
	public static Model process(URL url) throws IOException {
		return process(url.openStream(), url.toString());
	}

	/**
	 * An enumeration of the header structure types.
	 */
	private static enum HeaderStructureType { SIGNATURE, HEADER };

	/**
	 * An enumeration of the available header types.
	 */
	private static enum HeaderType {
		NULL,
		CHAR,
		INT8,
		INT16,
		INT32,
		INT64,
		STRING,
		BIN,
		STRING_ARRAY,
		I18NSTRING;

		/**
		 * Returns the appropriate header type identified by the supplied value.
		 */
		public static HeaderType get(int value) {
			try {
				return values()[value];
			} catch(IndexOutOfBoundsException e) {
				return null;
			}
		}
	}

	/**
	 * Interface implemented by header tags and signature tags.
	 */
	private static interface Tag {

		/**
		 * Returns the type of the tag.
		 */
		public HeaderType getType();

		/**
		 * Returns an appropriate name that can be used in XML documents to identify the tag.
		 */
		public String toXML();
	}

	/**
	 * An enumeration of the documented RPM signature tags.
	 */
	private static enum SignatureTag implements Tag {

		HEADERSIGNATURES(62,	HeaderType.BIN),
		RSA(268,	HeaderType.BIN),
		SHA1(269,	HeaderType.STRING),

		SIGSIZE(1000,	HeaderType.INT32),
		PGP(1002,	HeaderType.BIN),
		MD5(1004,	HeaderType.BIN),
		GPG(1005,	HeaderType.BIN),
		PAYLOADSIZE(1007,	HeaderType.INT32),
		SHA1HEADER(1010,	HeaderType.BIN),
		DSAHEADER(1011,	HeaderType.BIN),
		RSAHEADER(1012,	HeaderType.BIN);

		private static final HashMap<Integer, SignatureTag>	map = new HashMap<Integer, SignatureTag>();

		static {
			for (SignatureTag tag: values()) {
				map.put(tag.value, tag);
			}
		}

		private int	value;
		private HeaderType	type;

		private SignatureTag(int value, HeaderType type) {
			this.value = value;
			this.type = type;
		}

		public HeaderType getType() {
			return type;
		}

		public String toXML() {
			return name().toLowerCase();
		}

		/**
		 * Returns the appropriate signature tag identified by the supplied value.
		 */
		public static SignatureTag get(int value) {
			return map.get(value);
		}
	}

	private static enum HeaderTag implements Tag {
		HEADERI18NTABLE(100,	HeaderType.STRING_ARRAY),
		NAME(1000,	HeaderType.STRING),
		VERSION(1001,	HeaderType.STRING),
		RELEASE(1002,	HeaderType.STRING),
		EPOCH(1003,	HeaderType.INT32),
		SUMMARY(1004,	HeaderType.I18NSTRING),
		DESCRIPTION(1005,	HeaderType.I18NSTRING),
		BUILDTIME(1006,	HeaderType.INT32),
		BUILDHOST(1007,	HeaderType.STRING),
		SIZE(1009,	HeaderType.INT32),
		DISTRIBUTION(1010,	HeaderType.STRING),
		VENDOR(1011,	HeaderType.STRING),
		LICENSE(1014,	HeaderType.STRING),
		PACKAGER(1015,	HeaderType.STRING),
		GROUP(1016,	HeaderType.I18NSTRING),
		URL(1020,	HeaderType.STRING),
		OS(1021,	HeaderType.STRING),
		ARCH(1022,	HeaderType.STRING),
		SOURCERPM(1044,	HeaderType.STRING),
		FILEVERIFYFLAGS(1045,	HeaderType.INT32),
		ARCHIVESIZE(1046,	HeaderType.INT32),
		RPMVERSION(1064,	HeaderType.STRING),
		CHANGELOGTIME(1080,	HeaderType.INT32),
		CHANGELOGNAME (1081,	HeaderType.STRING_ARRAY),
		CHANGELOGTEXT(1082,	HeaderType.STRING_ARRAY),
		COOKIE(1094,	HeaderType.STRING),
		OPTFLAGS(1122,	HeaderType.STRING),
		PAYLOADFORMAT(1124,	HeaderType.STRING),
		PAYLOADCOMPRESSOR(1125,	HeaderType.STRING),
		PAYLOADFLAGS(1126,	HeaderType.STRING),
		RHNPLATFORM(1131,	HeaderType.STRING),
		PLATFORM(1132,	HeaderType.STRING),

		PREINPROG(1085, HeaderType.STRING),
		POSTINPROG(1086, HeaderType.STRING),
		PREUNPROG(1087, HeaderType.STRING),
		POSTUNPROG(1088, HeaderType.STRING),

		OLDFILENAMES(1027, HeaderType.STRING_ARRAY),
		FILESIZES(1028, HeaderType.INT32),
		FILEMODES(1030, HeaderType.INT16),
 		FILERDEVS(1033, HeaderType.INT16),
 		FILEMTIMES(1034, HeaderType.INT32),
		FILEMD5S(1035, HeaderType.STRING_ARRAY),
		FILELINKTOS(1036, HeaderType.STRING_ARRAY),
		FILEFLAGS(1037, HeaderType.INT32),
		FILEUSERNAME(1039, HeaderType.STRING_ARRAY),
		FILEGROUPNAME(1040, HeaderType.STRING_ARRAY),
 		FILEDEVICES(1095, HeaderType.INT32),
		FILEINODES(1096, HeaderType.INT32),
 		FILELANGS(1097, HeaderType.STRING_ARRAY),
		DIRINDEXES(1116, HeaderType.INT32),
 		BASENAMES(1117, HeaderType.STRING_ARRAY),
 		DIRNAMES(1118, HeaderType.STRING_ARRAY),
 		FILECOLORS(1140, HeaderType.INT32),
 		FILECLASS(1141, HeaderType.INT32),
 		CLASSDICT(1142, HeaderType.STRING_ARRAY),
 		FILEDEPENDSX(1143, HeaderType.INT32),
 		FILEDEPENDSN(1144, HeaderType.INT32),
 		DEPENDSDICT(1145, HeaderType.INT32),

		PROVIDENAME(1047, HeaderType.STRING_ARRAY),
		REQUIREFLAGS(1048, HeaderType.INT32),
		REQUIRENAME(1049, HeaderType.STRING_ARRAY),
		REQUIREVERSION(1050, HeaderType.STRING_ARRAY),
		CONFLICTFLAGS(1053, HeaderType.INT32),
		CONFLICTNAME(1054, HeaderType.STRING_ARRAY),
 		CONFLICTVERSION(1055, HeaderType.STRING_ARRAY),
 		OBSOLETENAME(1090, HeaderType.STRING_ARRAY),
 		PROVIDEFLAGS(1112, HeaderType.INT32),
		PROVIDEVERSION(1113, HeaderType.STRING_ARRAY),
 		OBSOLETEFLAGS(1114, HeaderType.INT32),
		OBSOLETEVERSION(1115, HeaderType.STRING_ARRAY);

		private static final HashMap<Integer, HeaderTag>	map = new HashMap<Integer, HeaderTag>();

		static {
			for (HeaderTag tag: values()) {
				map.put(tag.value, tag);
			}
		}

		private int	value;
		private HeaderType	type;

		private HeaderTag(int value, HeaderType type) {
			this.value = value;
			this.type = type;
		}

		public HeaderType getType() {
			return type;
		}

		public String toXML() {
			return name().toLowerCase();
		}

		/**
		 * Returns the appropriate header tag identified by the supplied value.
		 */
		public static HeaderTag get(int value) {
			return map.get(value);
		}
	}

	private void process(Map signature, Map header) {

		rpmResource = model.createResource(uri);
		rpmResource.addProperty(RDF.type, model.createResource(RPM_NS + "Package"));

		// serialize these signature tags
		process(
			signature,
			EnumSet.of(
				SignatureTag.GPG,
				SignatureTag.MD5,
				SignatureTag.PGP,
				SignatureTag.PAYLOADSIZE,
				SignatureTag.RSA,
				SignatureTag.SHA1
			)
		);

		// process URL
		if (header.containsKey(HeaderTag.URL)) {
			rpmResource.addProperty(
					model.createProperty(RPM_NS, "url"),
					model.createResource((String) header.get(HeaderTag.URL))
			);
		}

		// process build time
		if (header.containsKey(HeaderTag.BUILDTIME)) {
			final int	seconds = ((int[]) header.get(HeaderTag.BUILDTIME))[0];
			rpmResource.addProperty(
					model.createProperty(RPM_NS, "buildtime"),
					model.createTypedLiteral(toDateTime(seconds).toString(), XSD.dateTime.getURI())
			);
		}

		// process these header tags
		process(
			header,
			EnumSet.of(
				HeaderTag.ARCH,
				HeaderTag.ARCHIVESIZE,
				HeaderTag.BUILDHOST,
				HeaderTag.DESCRIPTION,
				HeaderTag.DISTRIBUTION,
				HeaderTag.EPOCH,
				HeaderTag.GROUP,
				HeaderTag.LICENSE,
				HeaderTag.NAME,
				HeaderTag.OPTFLAGS,
				HeaderTag.OS,
				HeaderTag.PACKAGER,
				HeaderTag.PAYLOADCOMPRESSOR,
				HeaderTag.PAYLOADFORMAT,
				HeaderTag.PLATFORM,
				HeaderTag.RELEASE,
				HeaderTag.RHNPLATFORM,
				HeaderTag.RPMVERSION,
				HeaderTag.SIZE,
				HeaderTag.SOURCERPM,
				HeaderTag.SUMMARY,
				HeaderTag.VENDOR,
				HeaderTag.VERSION
			)
		);

		// process required capabilities
            if (header.containsKey(HeaderTag.REQUIRENAME))
                process(
			"depends",
			(String[]) header.get(HeaderTag.REQUIRENAME),
			(String[]) header.get(HeaderTag.REQUIREVERSION),
			(int[]) header.get(HeaderTag.REQUIREFLAGS)
		);

		// process provided capabilities
            if (header.containsKey(HeaderTag.PROVIDENAME))
                process(
			"provides",
			(String[]) header.get(HeaderTag.PROVIDENAME),
			(String[]) header.get(HeaderTag.PROVIDEVERSION),
			(int[]) header.get(HeaderTag.PROVIDEFLAGS)
		);

		// process conflicts
		if (header.containsKey(HeaderTag.CONFLICTNAME))
			process(
				"conflicts",
				(String[]) header.get(HeaderTag.PROVIDENAME),
				(String[]) header.get(HeaderTag.PROVIDEVERSION),
				(int[]) header.get(HeaderTag.PROVIDEFLAGS)
			);

		// process obsoletes
		if (header.containsKey(HeaderTag.OBSOLETENAME))
			process(
				"conflicts",
				(String[]) header.get(HeaderTag.OBSOLETENAME),
				(String[]) header.get(HeaderTag.OBSOLETEVERSION),
				(int[]) header.get(HeaderTag.OBSOLETEFLAGS)
			);

		// process file information
		processFiles(header);

		// serialize change log
		processChangeLog(header);
	}

	private void process(String property, String[] name, String[] version, int[] flags) {
		final Property	p = model.createProperty(RPM_NS, property);
		for (int i = 0; i < name.length; ++i) {
			Resource	capability = model.createResource();
			capability.addProperty(RDF.type, model.createResource(RPM_NS + "Capability"));
			capability.addProperty(
					model.createProperty(RPM_NS, "name"),
					name[i]
			);
			if (! version[i].isEmpty()) {
				capability.addProperty(
						model.createProperty(RPM_NS, getVersionPropertyName(flags[i])),
						version[i]
				);
			}
			rpmResource.addProperty(p, capability);
		}
	}

	private void processFiles(Map header) {
		String[]	fileNames = null;

		if (header.containsKey(HeaderTag.OLDFILENAMES)) {

			// file names are not compressed
			fileNames = (String[]) header.get(HeaderTag.OLDFILENAMES);

		} else if (header.containsKey(HeaderTag.BASENAMES) && header.containsKey(HeaderTag.DIRNAMES) && header.containsKey(HeaderTag.DIRINDEXES)) {

			// file names are compressed
			String[]	baseNames = (String[]) header.get(HeaderTag.BASENAMES);
			String[]	dirNames = (String[]) header.get(HeaderTag.DIRNAMES);
			int[]	dirIndexes = (int[]) header.get(HeaderTag.DIRINDEXES);
			fileNames = new String[baseNames.length];
			for (int i = 0; i < fileNames.length; ++i) {
				String	dirName = dirNames[dirIndexes[i]];

				StringBuilder	sb = new StringBuilder(dirName);
				if (! dirName.endsWith("/")) sb.append('/');
				sb.append(baseNames[i]);

				fileNames[i] = sb.toString();
			}

		}

		int[]	fileSizes = (int[]) header.get(HeaderTag.FILESIZES);
		String[]	fileUserNames = (String[]) header.get(HeaderTag.FILEUSERNAME);
		String[]	fileGroupNames = (String[]) header.get(HeaderTag.FILEGROUPNAME);
		int[]	fileMTimes = (int[]) header.get(HeaderTag.FILEMTIMES);

		Bag	bag = model.createBag();
		rpmResource.addProperty(model.createProperty(RPM_NS, "files"), bag);

		for (int i = 0; i < fileNames.length; ++i) {
			Resource	file = model.createResource();
			file.addProperty(RDF.type, model.createResource(RPM_NS + "File"));
			file.addProperty(
				model.createProperty(RPM_NS, "name"),
				fileNames[i]
			);
			file.addLiteral(
				model.createProperty(RPM_NS, "size"),
				fileSizes[i]
			);
			file.addProperty(
				model.createProperty(RPM_NS, "username"),
				fileUserNames[i]
			);
			file.addProperty(
				model.createProperty(RPM_NS, "groupname"),
				fileGroupNames[i]
			);
			file.addProperty(
				model.createProperty(RPM_NS, "lastmodified"),
				model.createTypedLiteral(toDateTime(fileMTimes[i]).toString(), XSD.dateTime.getURI())
			);
			bag.add(file);
		}
	}

	private void processChangeLog(Map header) {
		if (header.containsKey(HeaderTag.CHANGELOGNAME) && header.containsKey(HeaderTag.CHANGELOGTEXT) && header.containsKey(HeaderTag.CHANGELOGTIME)) {
			String[]	changeLogName = (String[]) header.get(HeaderTag.CHANGELOGNAME);
			String[]	changeLogText = (String[]) header.get(HeaderTag.CHANGELOGTEXT);
			int[]	changeLogTime = (int[]) header.get(HeaderTag.CHANGELOGTIME);

			Seq	seq = model.createSeq();
			rpmResource.addProperty(model.createProperty(RPM_NS, "changelog"), seq);

			for (int i = 0; i < changeLogName.length; ++i) {
				Resource	entry = model.createResource();
				entry.addProperty(RDF.type, model.createResource(RPM_NS + "ChangeLogEntry"));

				Matcher	matcher = pattern.matcher(changeLogName[i]);
				if (matcher.matches()) {
					String	name = matcher.group(1).trim();
					String	mail = matcher.group(2);
					Resource	creator = model.createResource();
					creator.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"));
					creator.addProperty(
						model.createProperty(FOAF_NS, "name"),
						name
					);
					creator.addProperty(
						model.createProperty(FOAF_NS, "mbox"),
						model.createResource("mailto:" + mail)
					);
					entry.addProperty(DC.creator, creator);
				} else
					entry.addProperty(DC.creator, changeLogName[i]);
				entry.addProperty(
					DCTerms.created,
					model.createTypedLiteral(toDateTime(changeLogTime[i]).toString(), XSD.dateTime.getURI())
				);
				entry.addProperty(RDFS.comment, changeLogText[i]);
				seq.add(entry);
			}
		}
	}

	private void process(Map map, EnumSet tags) {
		Iterator	iterator = tags.iterator();
		while (iterator.hasNext())  {
			Tag	tag = (Tag) iterator.next();
			if (! map.containsKey(tag)) continue;
			RDFNode	value = null;
			switch (tag.getType()) {
			case CHAR:
				{
					// We can simply omit this type because it is not used currently (none of the documented siganature tags or header tags uses this type)
					break;
				}
			case INT8:
				{
					byte[]	byteArray = (byte[]) map.get(tag);
					if (byteArray.length > 1) {
						Seq	seq = model.createSeq();
						for (int i = 0; i < byteArray.length; ++i) {
							seq.add(
								model.createTypedLiteral(String.valueOf(byteArray[i]), XSD.xbyte.getURI())
							);
						}
						value = seq;
					} else
						value = model.createTypedLiteral(String.valueOf(byteArray[0]), XSD.xbyte.getURI());
					break;
				}
			case INT16:
				{
					short[]	shortArray = (short[]) map.get(tag);
					if (shortArray.length > 1) {
						Seq	seq = model.createSeq();
						for (int i = 0; i < shortArray.length; ++i) {
							seq.add(
								model.createTypedLiteral(String.valueOf(shortArray[i]), XSD.xshort.getURI())
							);
						}
						value = seq;
					} else
						value = model.createTypedLiteral(String.valueOf(shortArray[0]), XSD.xshort.getURI());
					break;
				}
			case INT32:
				{
					int[]	intArray = (int[]) map.get(tag);
					if (intArray.length > 1) {
						Seq	seq = model.createSeq();
						for (int i = 0; i < intArray.length; ++i)  {
							seq.add(model.createTypedLiteral(intArray[i]));
						}
						value = seq;
					} else
						value = model.createTypedLiteral(intArray[0]);
					break;
				}
			case INT64:
				{
					// We can simply omit this type because it is not used (not supported)
					break;
				}
			case STRING:
				{
					value = model.createLiteral((String) map.get(tag));
					break;
				}
			case BIN:
				{
					value = model.createTypedLiteral(
						new String(Base64.encodeBase64((byte[]) map.get(tag))),
						XSD.base64Binary.getURI()
					);
					break;
				}
			case STRING_ARRAY:
				{
					// serialize a string array as a Seq container
					Seq	seq = model.createSeq();
					String[]	array = (String[]) map.get(tag);
					for (int i = 0; i < array.length; ++i) {
						seq.add(model.createLiteral(array[i]));
					}
					value = seq;
					break;
				}
			case I18NSTRING:
				{
					String[]	array = (String[]) map.get(tag);
					if (array.length == 1) {
						value = model.createLiteral(array[0]);
					} else {
						// serialize a string array as an Alt container
						Alt	alt = model.createAlt();
						for (int i = 0; i < array.length; ++i) {
							alt.add(model.createLiteral(array[i]));
						}
						value = alt;
					}
					break;
				}
			}
			if (value != null)
				rpmResource.addProperty(model.createProperty(RPM_NS, tag.toXML()), value);
		}
	}

	private static interface DependencyFlags {
		public static final int	LESS = 2;
		public static final int	GREATER = 4; 
		public static final int	EQUAL = 8;
	}

	private static String getVersionPropertyName(int flags) {
		if ((flags & DependencyFlags.LESS) != 0) {

			return "minVersion" + ((flags & DependencyFlags.EQUAL) != 0 ? "Inclusive" : "Exclusive");

		} else if ((flags & DependencyFlags.GREATER) != 0) {

			return "maxVersion" + ((flags & DependencyFlags.EQUAL) != 0 ? "Inclusive" : "Exclusive");
		}
		if ((flags & DependencyFlags.EQUAL) != 0) return "version";

		return null;
	}

	/**
	 * Converts a POSIX time value to a W3C XML Schema <a href="http://www.w3.org/TR/xmlschema-2/#dateTime">dateTime</a> value.
	 *
	 * @param seconds
	 * @return
	 */
	private static XMLGregorianCalendar toDateTime(int seconds) {
		GregorianCalendar       cal = new GregorianCalendar();
		cal.setTimeInMillis(seconds * 1000L);
		try {
			return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
		} catch(DatatypeConfigurationException e) {
			return null;
		}
	}

}