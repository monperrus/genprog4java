package clegoues.genprog4java.Search;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import clegoues.genprog4java.fitness.Fitness;
import clegoues.genprog4java.main.Configuration;
import clegoues.genprog4java.mut.EditOperation;
import clegoues.genprog4java.rep.JavaRepresentation;
import clegoues.genprog4java.rep.Representation;
import ylyu1.wean.DataProcessor;
import ylyu1.wean.VariantCheckerMain;

public class GeneticProgramming<G extends EditOperation> extends Search<G>{
	//invariant checker mode got refactored into Configuration
	
	private int generationsRun = 0;
	public GeneticProgramming(Fitness engine) {
		super(engine);
	}


	/*
	 * prepares for GA by registering available mutations (including templates
	 * if applicable) and reducing the search space, and then generates the
	 * initial population, using [incoming_pop] if non-empty, or by randomly
	 * mutating the [original]. The resulting population is evaluated for
	 * fitness before being returned. This may terminate early if a repair is
	 * found in the initial population (by [calculate_fitness]).
	 * 
	 * @param original original variant
	 * 
	 * @param incoming_pop possibly empty, incoming population
	 * 
	 * @return initial_population generated by mutating the original
	 */
	protected Population<G> initialize(Representation<G> original,
			Population<G> incomingPopulation) throws RepairFoundException, GiveUpException {
		original.getLocalization().reduceSearchSpace();

		Population<G> initialPopulation = incomingPopulation;

		if (incomingPopulation != null
				&& incomingPopulation.size() > incomingPopulation.getPopsize()) {
			initialPopulation = incomingPopulation.firstN(incomingPopulation
					.getPopsize());
		} 
		int stillNeed = initialPopulation.getPopsize()*2
				- initialPopulation.size();
		if (stillNeed > 0) {
			initialPopulation.add(original.copy());
			stillNeed--;
		}
		for (int i = 0; i < stillNeed; i++) {
			Representation<G> newItem = original.copy();
			this.mutate(newItem);
			initialPopulation.add(newItem);
		}

		for (Representation<G> item : initialPopulation) {
			if (fitnessEngine.testFitness(0, item)) {
				this.noteSuccess(item, original, 0);
				if(!continueSearch) {
					throw new RepairFoundException();
				}
			}
			copyClassFilesIntoOutputDir(item); //relies on testFitness compiling the Representation item
		}
		return initialPopulation;
	}

	/*
	 * runs the genetic algorithm for a certain number of iterations, given the
	 * most recent/previous generation as input. Returns the last generation,
	 * unless it is killed early by the search strategy/fitness evaluation. The
	 * optional parameters are set to the obvious defaults if omitted.
	 * 
	 * @param start_gen optional; generation to start on (defaults to 1)
	 * 
	 * @param num_gens optional; number of generations to run (defaults to
	 * [generations])
	 * 
	 * @param incoming_population population produced by the previous iteration
	 * 
	 * @raise Found_Repair if a repair is found
	 * 
	 * @raise Max_evals if the maximum fitness evaluation count is reached
	 * 
	 * @return population produced by this iteration *)
	 */
	protected void runAlgorithm(Representation<G> original, Population<G> initialPopulation) throws RepairFoundException, GiveUpException {
		/*
		 * the bulk of run_ga is performed by the recursive inner helper
		 * function, which Claire modeled off the MatLab code sent to her by the
		 * UNM team
		 */
		logger.info("search: genetic algorithm begins\n");

		
		
		// Step 0: run daikon
		System.out.println("mode: "+Configuration.invariantCheckerMode);
		if(Configuration.invariantCheckerMode>0)
		{
			int trials = 0;
			while((trials<5)&&(!(new File(Configuration.workingDir+"/JUSTUSE.ywl")).exists()))
			{
				System.out.println("Here we are");
				VariantCheckerMain.runDaikon();
				trials++;
			}//VariantCheckerMain.checkInvariantOrig();
			if(!(new File(Configuration.workingDir+"/JUSTUSE.ywl")).exists())
			{
				DataProcessor.storeError("weirddaikon");
				Runtime.getRuntime().exit(1);
			}
		}
		
		
		assert (Search.generations >= 0);
		Population<G> incomingPopulation = this.initialize(original,
				initialPopulation);
		int gen = 1;
		int checked = 0;
		
		while (gen < Search.generations) {
			VariantCheckerMain.turn=gen;
			logger.info("search: generation" + gen);
			generationsRun++;
			assert (initialPopulation.getPopsize() > 0);
			
			try {
			
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("DATAOFSEED"+Configuration.seed+"Gen"+gen+".ddd"));
			oos.writeObject(incomingPopulation);
			oos.flush();
			oos.close();
			}catch(IOException e) {}
			
			if(Configuration.invariantCheckerMode>1||(Configuration.invariantCheckerMode==1&&gen==1))
			{
				//if(gen==1) {
				// Step 0.5: Check Invariant
				VariantCheckerMain.checkInvariant(incomingPopulation);//}
			}
			
			ArrayList<Double> fitscores = new ArrayList<Double>();
			for(Representation<G> item : incomingPopulation)
			{
				fitscores.add(item.getFitness());
			}
			DataProcessor.fitscores.add(fitscores);
			
			// Step 1: selection
			incomingPopulation.selection(incomingPopulation.getPopsize());
			
			// step 2: crossover
			incomingPopulation.crossover(original);

			// step 3: mutation
			ArrayList<Representation<G>> newlist = new ArrayList<Representation<G>>();
			for (Representation<G> item : incomingPopulation) {
				
				Representation<G> newItem =item.copy();
				this.mutate(newItem);
				newlist.add(newItem);
			}
			incomingPopulation.getPopulation().addAll(newlist);

			// step 4: fitness
			for (Representation<G> item : incomingPopulation) {
				if (fitnessEngine.testFitness(gen, item)) {
					this.noteSuccess(item, original, gen);
					if(!continueSearch) 
						return;
				}
				copyClassFilesIntoOutputDir(item); //relies on testFitness compiling the Representation item
			}
			gen++;
		}
                System.out.println("Variant number: "+JavaRepresentation.sequence);
	}
	
	/**
	 * Copies the compiled source files (from classSourceFolder, as defined in the .config file) to the outputDir (default for experiments: the tmp folder)
	 * @param item
	 */
	private void copyClassFilesIntoOutputDir(Representation<G> item)
	{
		if (item.getVariantFolder().equals(""))
		{
			//if there's no variant folder name, do nothing
			return;
		}
		
		String copyDestination = Configuration.outputDir + //no space added
				(Configuration.outputDir.endsWith(File.separator) ? "" : File.separator) + //add a separator if necessary
				"d_" + item.getVariantFolder();
		
		File dFolder = new File(copyDestination);
		if(!dFolder.exists())
			dFolder.mkdirs();
		
		File classSourceFolderFile = new File(Configuration.classSourceFolder);
		if(!classSourceFolderFile.exists())
			System.err.println("classSourceFolder does not exist");
		
		
		/*
		CommandLine cpCommand = CommandLine.parse(
				"cp -R " +
				Configuration.classSourceFolder + //no space added
				(Configuration.classSourceFolder.endsWith(File.separator) ? "" : File.separator) + //add a separator if necessary
				"* " + //a wildcard char may or may not be needed
				copyDestination
				);
		*/
		CommandLine cpCommand = CommandLine.parse(
				"rsync -r " +
				Configuration.classSourceFolder + //no space added
				(Configuration.classSourceFolder.endsWith(File.separator) ? "" : File.separator) + //add a separator if necessary
				" " +
				copyDestination
				);
		
		System.err.println("cp command: " + cpCommand);
		
		ExecuteWatchdog watchdog = new ExecuteWatchdog(1000000);
		DefaultExecutor executor = new DefaultExecutor();
		String workingDirectory = System.getProperty("user.dir");
		executor.setWorkingDirectory(new File(workingDirectory));
		executor.setWatchdog(watchdog);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		executor.setExitValue(0);

		executor.setStreamHandler(new PumpStreamHandler(out));
		
		try
		{
			executor.execute(cpCommand);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try
			{
				out.flush();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
