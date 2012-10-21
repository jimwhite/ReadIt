package eiti

import edu.stanford.nlp.ling.IndexedWord

class Model
{
    // Lookup table to get slots by label.
    static Map<String, Slot> slots = [:]

    static Map<String, Filler> fillers = [:]

    // Wrapped constructors to populate the lookup tables.

    static Filler filler(String label)
    {
        Filler f = new Filler(label: label)
        fillers[label] = f
        f
    }

    static Filler filler(Filler f)
    {
        fillers[f.label] = f
        f
    }

    static Slot slot(String label, boolean multiple, Filler filler)
    {
        Slot s = new Slot(label, multiple, filler)
        slots[label] = s
        s
    }

    static Slot slot(Slot s)
    {
        slots[s.label] = s
        s
    }

    // Slot filler type classes.

    static PER = filler(new Filler('PER') {
        boolean filter(Answer answer) {
            (answer.words.size() > 1) && (answer.words.every { IndexedWord word -> word.lemma().matches(/(?i).*[aeiouy].*/)})
        }
    })

    static GPE = filler('GPE')
    static CRIMINAL_CHARGE = filler('CRIMINAL_CHARGE')

    static ORG = filler(new Filler('ORG') {
        boolean filter(Answer answer) {
            def fewer_words = ner_words(answer, 'ORGANIZATION')

            if (fewer_words) answer.words = fewer_words

            true
        }
    })

    static RELIGION = filler(new Filler('RELIGION') {
        boolean filter(Answer answer) {
            def fewer_words = ner_words(answer, 'RELIGION')

            if (!fewer_words) {
                fewer_words = ner_words(answer, 'ORGANIZATION')
            }

            answer.words = fewer_words

            answer.words.size() > 0
        }
    })

    static TITLE = filler(new Filler('TITLE') {
        boolean filter(Answer answer) {
            answer.words = ner_words(answer, 'TITLE')
            answer.words.size() > 0
        }
    })

    static DATE = filler('DATE')

    static NUMBER = filler(new Filler('NUMBER') {
        boolean filter(Answer answer) {
            !(answer.words.any { it.value().equalsIgnoreCase("of") })
        }
    })

    static LOCATION = filler('LOCATION')

    static COUNTRY = filler(new Filler('COUNTRY') {
        boolean filter(Answer answer) {
            def fewer_words = ner_words(answer, 'COUNTRY')

            answer.words = fewer_words

            answer.words.size() > 0
        }
    })

    static NATIONALITY = filler ('NATIONALITY')
    static CAUSE_OF_DEATH = filler('CAUSE_OF_DEATH')

    static CITY = filler(new Filler('CITY') {
        boolean filter(Answer answer) {
            def state_index = answer.words.findIndexOf { IndexedWord w -> w.ner() == 'STATE_OR_PROVINCE' }

            if (state_index > 0) {
                answer.words = answer.words.subList(0, state_index)
            } else if (state_index == 0) {
                answer.words = []
            }

            answer.words.size() > 0
        }
    })

    static STATE_OR_PROVINCE = filler(new Filler('STATE_OR_PROVINCE') {
        boolean filter(Answer answer) {
            def fewer_words = ner_words(answer, 'STATE_OR_PROVINCE')

            answer.words = fewer_words

            answer.words.size() > 0
        }
    })

    // Flag values for indicating whether a slot is limited to a single values or may have multiple values.

    static SINGLE = false
    static MULTIPLE = true

    // Slot definition singletons.

    static PER_ALTERNATE_NAMES = slot(new Slot('per:alternate_names', MULTIPLE, PER) {
        boolean redundant(String name, Answer answer)
        {
            def value = answer.value.toLowerCase()

            name = name.toLowerCase()

            (name.startsWith(value) || name.endsWith(value))
        }
    })

//    per:age	single	NUMBER
//    per:alternate_names	list	PERSON
//    per:cause_of_death	single	CAUSE_OF_DEATH
//    per:charges	list	CRIMINAL_CHARGE
//    per:children	list	PERSON
//    per:date_of_birth	single	DATE
//    per:date_of_death	single	DATE
//    per:employee_of	list	ORGANIZATION
//    per:member_of	list	ORGANIZATION
//    per:origin	list	NATIONALITY
//    per:other_family	list	PERSON
//    per:parents	list	PERSON
//    per:country_of_birth	single	COUNTRY
//    per:stateorprovince_of_birth	single	STATE_OR_PROVINCE
//    per:city_of_birth	single	LOCATION
//    per:country_of_death	single	COUNTRY
//    per:stateorprovince_of_death	single	STATE_OR_PROVINCE
//    per:city_of_death	single	LOCATION
//    per:religion	single	RELIGION
//    per:countries_of_residence	list	COUNTRY
//    per:stateorprovinces_of_residence	list	STATE_OR_PROVINCE
//    per:cities_of_residence	list	LOCATION
//    per:schools_attended	list	ORGANIZATION
//    per:siblings	list	PERSON
//    per:spouse	list	PERSON
//    per:title	list	TITLE

    static PER_AGE = slot('per:age',SINGLE,NUMBER)
    static PER_CAUSE_OF_DEATH = slot('per:cause_of_death',SINGLE,CAUSE_OF_DEATH)
    static PER_CHARGES = slot('per:charges',MULTIPLE,CRIMINAL_CHARGE)
    static PER_CHILDREN = slot('per:children',MULTIPLE,PER)
    static PER_DATE_OF_BIRTH = slot('per:date_of_birth',SINGLE,DATE)
    static PER_DATE_OF_DEATH = slot('per:date_of_death',SINGLE,DATE)
    static PER_EMPLOYEE_OF = slot('per:employee_of',MULTIPLE,ORG)
    static PER_MEMBER_OF = slot('per:member_of',MULTIPLE,ORG)
    static PER_ORIGIN = slot('per:origin',MULTIPLE,NATIONALITY)
    static PER_OTHER_FAMILY = slot('per:other_family',MULTIPLE,PER)
    static PER_PARENTS = slot('per:parents',MULTIPLE,PER)
    static PER_COUNTRY_OF_BIRTH = slot('per:country_of_birth',SINGLE,COUNTRY)
    static PER_STATEORPROVINCE_OF_BIRTH = slot('per:stateorprovince_of_birth',SINGLE,STATE_OR_PROVINCE)
    static PER_CITY_OF_BIRTH = slot('per:city_of_birth',SINGLE,CITY)
    static PER_COUNTRY_OF_DEATH = slot('per:country_of_death',SINGLE,COUNTRY)
    static PER_STATEORPROVINCE_OF_DEATH = slot('per:stateorprovince_of_death',SINGLE,STATE_OR_PROVINCE)
    static PER_CITY_OF_DEATH = slot('per:city_of_death',SINGLE,CITY)
    static PER_RELIGION = slot('per:religion',SINGLE,RELIGION)
    static PER_COUNTRIES_OF_RESIDENCE = slot('per:countries_of_residence',MULTIPLE,COUNTRY)
    static PER_STATEORPROVINCES_OF_RESIDENCE = slot('per:stateorprovinces_of_residence',MULTIPLE,STATE_OR_PROVINCE)
    static PER_CITIES_OF_RESIDENCE = slot('per:cities_of_residence',MULTIPLE,CITY)
    static PER_SCHOOLS_ATTENDED = slot('per:schools_attended',MULTIPLE,ORG)
    static PER_SIBLINGS = slot('per:siblings',MULTIPLE,PER)
    static PER_SPOUSE = slot('per:spouse',MULTIPLE,PER)
    static PER_TITLE = slot('per:title',MULTIPLE,TITLE)

    // Helper routines for filter functions.

    // Return a list of contiguous words in the answer that are annotated with the given NER value,
    // or the empty list if there are none.
    static List<IndexedWord> ner_words(Answer answer, String nerValue)
    {
        def words = []

        def ner_index = answer.words.findIndexOf { IndexedWord w -> w.ner() == nerValue }

        if (ner_index >= 0) {
            words = answer.words.subList(ner_index, answer.words.size())

            def non_ner_index = words.findIndexOf { IndexedWord w -> w.ner() != nerValue }

            if (non_ner_index > 0) words = words.take(non_ner_index)
        }

        words
    }

    static {
//        println slots
//        println fillers
    }
}
