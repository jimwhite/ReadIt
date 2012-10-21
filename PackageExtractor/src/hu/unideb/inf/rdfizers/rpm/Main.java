package hu.unideb.inf.rdfizers.rpm;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * Command line interface to the {@link ModelBuilder} class.
 */
public class Main {

	public static Options options = new Options();

	static {
		options.addOption("h", "help", false, "display this help and exit");
		options.addOption(OptionBuilder
			.withLongOpt("language")
			.hasArg()
			.withArgName("language")
			.withDescription("write output in the language specified (N-TRIPLES, TURTLE, RDF/XML, RDF/XML-ABBREV, default: RDF/XML)")
			.create("l")
		);
		options.addOption(OptionBuilder
			.withLongOpt("output")
			.hasArg()
			.withArgName("file")
			.withDescription("write output to the file specified instead of standard output")
			.create("o")
		);
		options.addOptionGroup(
			new OptionGroup()
				.addOption(OptionBuilder
					.withLongOpt("file")
					.hasArg()
					.withArgName("file")
					.withDescription("read input from the file specified")
					.create("f"))
				.addOption(OptionBuilder
					.withLongOpt("url")
					.hasArg()
					.withArgName("url")
					.withDescription("read input from the URL specified")
					.create("u")
			)
		);
		options.addOption(OptionBuilder
			.withLongOpt("omit-changelog")
			.withDescription("omit changelog")
			.create("oc")
		);
		options.addOption(OptionBuilder
			.withLongOpt("omit-deps")
			.withDescription("omit dependencies")
			.create("od")
		);
		options.addOption(OptionBuilder
			.withLongOpt("omit-files")
			.withDescription("omit files")
			.create("of")
		);
	}

	private static void printHelp() {
		new HelpFormatter().printHelp("java " + Main.class.getName() + " [options]", options);
	}

	public static void main(String[] args) {
		CommandLineParser	parser = new BasicParser();
		CommandLine	cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch(ParseException e) {
			System.err.println(e.getMessage());
			printHelp();
			System.exit(1);
		}
		if (cmd.hasOption("h")) {
			printHelp();
			System.exit(0);
		}
		if (! cmd.hasOption("f") && ! cmd.hasOption("u")) {
			System.err.println("Either --file or --url must be specified");
			printHelp();
			System.exit(1);
		}
		try {
			Model	model = null;
			if (cmd.hasOption("f"))
				model = ModelBuilder.process(cmd.getOptionValue("f"));
			else if (cmd.hasOption("u"))
				model = ModelBuilder.process(new URL(cmd.getOptionValue("u")));
			model.write(
				cmd.hasOption("o") ? new FileOutputStream(cmd.getOptionValue("o")) : System.out,
				cmd.getOptionValue("l", "RDF/XML")
			);
		} catch (IOException e) {
			System.err.println(e);
		}
	}

}