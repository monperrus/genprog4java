package clegoues.genprog4java.Search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.TreeSet;

import clegoues.genprog4java.main.Configuration;
import clegoues.genprog4java.mut.EditOperation;
import clegoues.genprog4java.rep.Representation;
import clegoues.genprog4java.util.GlobalUtils;

public class Population<G extends EditOperation> implements Iterable<Representation<G>>{

	private static int popsize = 40;
	private static double crossp = 0.5; 

	private static String incomingPop = "";
	private int tournamentK = 2;
	private static String outputFormat = "txt";
	private double tournamentP = 1.0;
	private static String crossover = "onepoint";
	private ArrayList<Representation<G>> population = new ArrayList<Representation<G>>(this.popsize);

	public Population() {

	}
	public Population(ArrayList<Representation<G>> smallerPop) {
		this.population = smallerPop;
	}

	protected ArrayList<Representation<G>> getPopulation() {
		return this.population;
	}

	public int getPopsize() {
		return Population.popsize;
	}
	public static void configure(Properties prop) {
		if(prop.getProperty("crossp") != null) {
			crossp = Double.parseDouble(prop.getProperty("crossp").trim());
		}
		if(prop.getProperty("popsize") != null) {
			popsize = Integer.parseInt(prop.getProperty("popsize").trim());
		}
		if(prop.getProperty("crossover") != null) {
			crossover = prop.getProperty("crossover").trim();
		}
	}

	/* I think that generate makes no sense in java */

	/* {b serialize} serializes a population to disk.  The first variant is
	      optionally instructed to print out the global information necessary for a
	      collection of representations.  The remaining variants print out only
	      their variant-specific local information */
	void serialize() {
		throw new UnsupportedOperationException();
		/*
			  let serialize ?out_channel (population : ('a,'b) t) (filename : string) =
			    match !output_format with
			      "bin" | "binary" ->
			        let fout = 
			          match out_channel with
			            Some(v) -> v
			          | None -> open_out_bin filename 
			        in
			          Marshal.to_channel fout (population_version) [] ;
			          liter (fun variant -> variant#serialize ?out_channel:(Some(fout)) ?global_info:(Some(false)) filename) population;
			          if out_channel = None then close_out fout
			    | "txt" ->
			      debug "serializing population to txt; ?out_channel ignored\n";
			      let fout = open_out filename in 
			        liter (fun variant -> 
			          let name = variant#name () in
			            output_string fout (name^"\n"))
			          population;
			        if out_channel = None then close_out fout
		 */
	}


	/* {b deserialize} deserializes a population from disk, to be used as
	      incoming_pop.  The incoming variant is assumed to have loaded the global
	      state (which CLG doesn't love so she might change it).  Remaining variants
	      are read in individually, using only their own local information */
	/* deserialize can fail if the file does not conform to the expected format
	     for Marshal or if there is a version mismatch between the population module
	     that wrote the binary file and this one (that is loading it). */
	void deserialize(String filename) {
		throw new UnsupportedOperationException();
		/*
			  let deserialize ?in_channel filename original = 
			    (* the original should have loaded the global state *)
			    let fin = 
			      match in_channel with
			        Some(v) -> v
			      | None -> open_in_bin filename in
			    let pop = ref [original] in
			      try
			        if !output_format = "txt" then 
			          failwith "txt format, skipping binary attempt";
			        let version = Marshal.from_channel fin in
			          if version <> population_version then begin
			            debug "population: %s has old version: %s\n" filename version;
			            failwith "version mismatch" 
			          end ;
			          let attempt = ref 1 in
			          try
			            while true do
			              debug "attempt %d\n" !attempt; incr attempt;
			              let rep' = original#copy () in
			                rep'#deserialize ?in_channel:(Some(fin)) ?global_info:(None) filename;
			                pop := rep'::!pop
			            done; !pop
			          with End_of_file -> !pop
			      with _ -> begin
			        close_in fin;
			        pop := [original];
			        try
			          let individuals = get_lines filename in 
			            liter
			              (fun genome ->
			                let copy = original#copy() in
			                  copy#load_genome_from_string genome;
			                  pop := copy :: !pop
			              ) individuals; !pop
			        with End_of_file -> !pop
			      end
		 */
	}

	/* {b tournament_selection} variant_comparison_function population
    desired_pop_size uses tournament selction to select desired_pop_size
    variants from population using variant_comparison_function to compare
    individuals, if specified, and variant fitness if not.  Returns a subset
    of the population.  */
	private Representation<G> selectOne() {
		Collections.shuffle(population);
		List<Representation<G>> pool = population.subList(0, tournamentK);
		// FIXME: in what order should this be sorted?  Ascending, or D?
		TreeSet<Representation<G>> sorted = new TreeSet<Representation<G>>(pool);
		double step = 0.0;
		for(Representation<G> indiv : sorted) {
			boolean taken = false;
			if(this.tournamentP >= 1.0) {
				taken = true;
			} else {
				double requiredProb = this.tournamentP * Math.pow((1.0 - this.tournamentP), step);
				double random = Configuration.randomizer.nextDouble();
				if(random <= requiredProb) {
					taken = true;
				}
			}
			if(taken) {
				return indiv;
			} else {
				step += 1.0;
			}
		}
		return population.get(0); // FIXME: this should never happen, right?
	}
	private ArrayList<Representation<G>> tournamentSelection(int desired) {
		assert(desired >= 0);
		assert(tournamentK >= 1);
		assert(this.tournamentP >= 0.0);
		assert(this.tournamentP <= 1.0) ;
		assert(population.size() >= 0);
		ArrayList<Representation<G>> result = new ArrayList<Representation<G>>();

		for(int i = 0 ; i < desired; i++) {
			result.add(selectOne());
		}
		return result; 
	}


	public void add (Representation<G> newItem) {
		population.add(newItem);
	}

	/* Crossover is an operation on more than one variant, which is why it
			appears here.  We currently have one-point crossover implemented on
			variants of both stable and variable length, patch_subset_crossover, which
			is something like uniform crossover (but which works on all
			representations now, not just cilRep patch) and "ast_old_behavior", which
			Claire hasn't fixed yet.  The nitty-gritty of how to combine
			representation genomes to accomplish crossover has been mostly moved to
			the representation classes, so this implementation doesn't know much about
			particular genomes.  Crossback implements one-point between variants and
			the original. *)
			(* this implements the old AST/WP crossover behavior, typically intended to be
			used on the patch representation.  I don't like keeping it around, since
			the point of refactoring is to decouple the evolutionary behavior from the
			representation.  I'm still thinking about it *)
			(* this can fail if the edit histories contain unexpected elements, such as
			crossover, or if load_genome_from_string fails (which is likely, since it's
			not implemented across the board, which is why I'm mentioning it in this
			comment) */
	// I have not implemented crossoverPatchOldBehavior


	//  Patch Subset Crossover; works on all representations even though it was
	// originally designed just for cilrep patch
	private ArrayList<Representation<G>> crossoverPatchSubset(Representation<G> original, Representation<G> variant1, Representation<G> variant2) {
		ArrayList<G> g1 = variant1.getGenome();
		ArrayList<G> g2 = variant2.getGenome();
		ArrayList<G> g1g2 = new ArrayList<G>(g1);
		g1g2.addAll(g2);
		ArrayList<G> g2g1 = new ArrayList<G>(g2);
		g2g1.addAll(g1);

		ArrayList<G> newG1 = new ArrayList<G>();
		ArrayList<G> newG2 = new ArrayList<G>();

		for(G ele : g1g2) {
			if(GlobalUtils.probability(crossp)) {
				newG1.add(ele);
			}
		}
		for(G ele : g2g1) {
			if(GlobalUtils.probability(crossp)) {
				newG2.add(ele);
			}
		}
		Representation<G> c1 = original.copy();
		Representation<G> c2 = original.copy();
		c1.setGenome(newG1);
		c2.setGenome(newG2);
		ArrayList<Representation<G>> retval = new ArrayList<Representation<G>>();
		retval.add(c1);
		retval.add(c2);
		return retval;
	}

	private ArrayList<Representation<G>> crossoverOnePoint(Representation<G> original, Representation<G> variant1, Representation<G> variant2) {
		Representation<G> child1 = original.copy();
		Representation<G> child2 = original.copy();
		// in the OCaml, to support the flat crossover on binRep for Eric, I had a convoluted thing
		// where you had to query variants to figure out which crossover points were legal
		// as I have no plans to support binary repair in Java at the moment, I'm doing
		// the easy thing here instead.
		ArrayList<G> g1 = variant1.getGenome();
		ArrayList<G> g2 = variant2.getGenome();
		int point1 = Configuration.randomizer.nextInt(g1.size());
		int point2 = point1;
		if(original.getVariableLength()) {
			point2 = Configuration.randomizer.nextInt(g2.size());
		}

		ArrayList<G> newg1 = new ArrayList<G>();
		ArrayList<G> newg2 = new ArrayList<G>();
		newg1.addAll(g1.subList(0, point1));
		newg1.addAll(g2.subList(point2,g2.size()));
		newg2.addAll(g2.subList(0, point2));
		newg2.addAll(g1.subList(point1, g1.size())); // FIXME: inclusive?

		// FIXME: add crossover to history?
		child1.setGenome(newg1);
		child2.setGenome(newg2);
		ArrayList<Representation<G>> retval = new ArrayList<Representation<G>>();
		retval.add(child1);
		retval.add(child2);
		return retval;
	}

	/* do_cross original variant1 variant2 performs crossover on variant1 and
	variant2, producing two children [child1;child2] as a result.  Dispatches
	to the appropriate crossover function based on command-line options *)
	do_cross can fail if given an unexpected crossover option from the command
	line */
	private ArrayList<Representation<G>> doCross(Representation<G> original, Representation<G> variant1, Representation<G> variant2) {
		if(crossover.equals("one") || crossover.equals("onepoint") || crossover.equals("pstch-one-point")) {
			return crossoverOnePoint(original,variant1,variant2);
		}
		if(crossover.equals("back")) {
			return crossoverOnePoint(original, variant1, original);
		}
		if(crossover.equals("uniform")) {
			return crossoverPatchSubset(original,variant1,variant2);
		}
		throw new UnsupportedOperationException("Population: unrecognized crossover: " + crossover);

	}
/* crossover population original_variant performs crossover over the entire
			population, returning a new population with both the old and the new
			variants */
	
	public void crossover(Representation<G> original) {
		Collections.shuffle(population,Configuration.randomizer);
		ArrayList<Representation<G>> output = new ArrayList<Representation<G>>();
		int half = population.size() / 2;
		for(int it = 0 ; it < half-1; it++) {
			Representation<G> parent1 = population.get(it);
			Representation<G> parent2 = population.get(it + half);
			if(GlobalUtils.probability(crossp)) {
				ArrayList<Representation<G>> children = this.doCross(original, parent1, parent2);
				output.add(parent1);
				output.add(parent2);
				output.addAll(children);
			}
		}
		this.population = output; // FIXME I think
	}

	public Population<G> firstN(int desiredSize) {
		List<Representation<G>> smallerPop = population.subList(0, desiredSize);
		return new Population<G>((ArrayList<Representation<G>>) smallerPop);
	}

	public int size() {
		return population.size();
	}
	@Override
	public Iterator<Representation<G>> iterator() {
		return population.iterator(); 	
	}

	public void selection(int popsize) {
		this.tournamentSelection(popsize);

	}



}
