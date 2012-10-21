package scorer;// CheckSFResponse
//   checks for errors in 2010-format KBP slot filling response
//     correct number of fields
//     valid slot name
//     same run id for all responses in a file
//     single-valued slot has at most 1 response
//     list-valued slot has at most one NIL response
//     list-valued slot does not have both a NIL response and a non-NIL response

// author:  Ralph Grishman

// ver. 1.0
// July 10, 2010

import java.io.*;
import java.util.*;

public class CheckSFResponse {

    static String line;
    static String runId;
    static Set<String> querySlots = new TreeSet<String>();
    static Map<String, Integer> nilResponseCount = new TreeMap<String, Integer>();
    static Map<String, Integer> non_nilResponseCount = new TreeMap<String, Integer>();

    /**
     *  CheckSFResponse <response file>
     */

    public static void main (String[] args) throws IOException {

	if (args.length != 1) {
	    System.out.println ("CheckSFResponse must be invoked with 1 argument");
	    System.exit(1);
	}
	String responseFile = args[0];

	BufferedReader responseReader = null;
	try {
	    responseReader = new BufferedReader (new FileReader(responseFile));
	} catch (FileNotFoundException e) {
	    System.out.println ("Unable to open response file " + responseFile);
	    System.exit (1);
	}

	while ((line = responseReader.readLine()) != null) {
	    String[] fields = line.trim().split("\\s+", 5);
	    if (fields.length < 4) {
		System.out.println ("Error: " + fields.length + " fields (should be 4 or 5)");
		System.out.println (" Line:  " + line);
		System.out.println ();
		continue;
	    }
	    // column 1 = query id
	    if (! fields[0].startsWith("SF")) {
		System.out.println ("Error: query id does not begin with 'SF'");
		System.out.println (" Line:  " + line);
		System.out.println ();
		continue;
	    }
	    // column 2 = slot name
	    if (!checkSlotName (fields[1])) continue;
	    // column 3 = run id
	    // should be the same for all lines
	    if (runId == null) {
		runId = fields[2];
	    } else {
		if (!(fields[2].equals(runId))) {
		    System.out.println ("Error: inconsistent run ID's");
		    System.out.println (" Line:  " + line);
		    System.out.println ();
		    continue;
		}
	    }
	    // column 4 = docId
	    // column 5 = response
	    // (allow either blank or explicict NIL response)
	    if (fields[3].equals("NIL")) {
		if (fields.length == 5 && !(fields[4].equals("NIL"))) {
		    System.out.println ("Error: NIL docId with non-NIL response");
		    System.out.println (" Line:  " + line);
		    System.out.println ();
		    continue;
		} else {
		    String key = fields[0] + "|" + fields[1];
		    querySlots.add(key);
		    if(nilResponseCount.get(key) == null)
			nilResponseCount.put(key, 0);
		    if(non_nilResponseCount.get(key) == null)
			non_nilResponseCount.put(key, 0);
		    nilResponseCount.put(key, nilResponseCount.get(key) + 1);
		}
	    } else {
		if (fields.length == 4 || fields[4].equals("NIL")) {
		    System.out.println ("Error: non-NIL docId with NIL response");
		    System.out.println (" Line:  " + line);
		    System.out.println ();
		    continue;
		} else {
		    String key = fields[0] + "|" + fields[1];
		    querySlots.add(key);
		    if(nilResponseCount.get(key) == null)
			nilResponseCount.put(key, 0);
		    if(non_nilResponseCount.get(key) == null)
			non_nilResponseCount.put(key, 0);
		    non_nilResponseCount.put(key, non_nilResponseCount.get(key) + 1);
		}
	    }
	}

	// checks on combined responses for a given slot

	for (String querySlot : querySlots) {
	    String query = (querySlot.split("\\|"))[0];
	    String slot = (querySlot.split("\\|"))[1];
	    if (singleValuedSlots.contains(slot)) {
		if (nilResponseCount.get(querySlot) + non_nilResponseCount.get(querySlot) > 1)
		    System.out.println ("Multiple responses for single-valued slot " +
			                slot + " for query " + query);
	    } else {
		if (nilResponseCount.get(querySlot) > 1)
		    System.out.println ("Multiple NIL responses for slot " +
			                slot + " for query " + query);
		if (nilResponseCount.get(querySlot) > 0 && non_nilResponseCount.get(querySlot) > 0)
		    System.out.println ("Both NIL and non-NIL responses for slot " +
			                slot + " for query " + query);
	    }
	}
    }

    static List<String> singleValuedSlots = Arrays.asList(
        "per:date_of_birth",
        "per:age",
        "per:country_of_birth",
        "per:stateorprovince_of_birth",
        "per:city_of_birth",
        "per:date_of_death",
        "per:country_of_death",
        "per:stateorprovince_of_death",
        "per:city_of_death",
        "per:cause_of_death",
        "per:religion",
        "org:number_of_employees/members",
        "org:founded",
        "org:dissolved",
        "org:country_of_headquarters",
        "org:stateorprovince_of_headquarters",
        "org:city_of_headquarters",
        "org:website");

    static List<String> listValuedSlots = Arrays.asList(
        "per:alternate_names",
        "per:origin",
        "per:countries_of_residence",
        "per:stateorprovinces_of_residence",
        "per:cities_of_residence",
        "per:schools_attended",
        "per:title",
        "per:member_of",
        "per:employee_of",
        "per:spouse",
        "per:children",
        "per:parents",
        "per:siblings",
        "per:other_family",
        "per:charges",
        "org:alternate_names",
        "org:political/religious_affiliation",
        "org:top_members/employees",
        "org:members",
        "org:member_of",
        "org:subsidiaries",
        "org:parents",
        "org:founded_by",
        "org:shareholders");

    /**
     *  check that 'field' has a valid slot name
     */

    static boolean checkSlotName (String field) {
	if (singleValuedSlots.contains(field) || listValuedSlots.contains(field))
	    return true;
	System.out.println ("Error:  invalid slot name " + field);
	System.out.println (" Line:  " + line);
	System.out.println ();
	return false;
    }

}
