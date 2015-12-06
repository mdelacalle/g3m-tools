

package com.glob3mobile.tools.tiling;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.glob3mobile.tools.tiling.pyramid.MergedPyramid;
import com.glob3mobile.tools.tiling.pyramid.Pyramid;
import com.glob3mobile.tools.tiling.pyramid.SourcePyramid;
import com.glob3mobile.tools.tiling.pyramid.WebMercatorPyramid;
import com.glob3mobile.utils.IOUtils;
import com.glob3mobile.utils.Logger;
import com.glob3mobile.utils.Progress;


public class TilesMixer {

   public static void processSubdirectories(final Pyramid pyramid,
                                            final String inputDirectoryName,
                                            final String outputDirectoryName,
                                            final float jpegQuality) throws IOException {
      final File inputDirectory = new File(inputDirectoryName);
      if (!inputDirectory.exists()) {
         throw new IOException("Input directory \"" + inputDirectoryName + "\" doesn't exist");
      }

      if (!inputDirectory.isDirectory()) {
         throw new IOException("\"" + inputDirectoryName + "\" is not a directory");
      }

      final List<String> inputDirectoriesNames = //
      Arrays.asList(inputDirectory.list((dir,
                                         name) -> name.endsWith(".tiles") && new File(dir, name).isDirectory())) //
      .stream() //
      .map(source -> new File(inputDirectory, source).getAbsolutePath()) //
      .collect(Collectors.toList());

      final TilesMixer mixer = new TilesMixer(pyramid, inputDirectoriesNames, outputDirectoryName, jpegQuality);
      mixer.process();
   }


   public static void processDirectories(final Pyramid pyramid,
                                         final List<String> inputDirectoriesNames,
                                         final String outputDirectoryName,
                                         final float jpegQuality) throws IOException {
      final TilesMixer mixer = new TilesMixer(pyramid, inputDirectoriesNames, outputDirectoryName, jpegQuality);
      mixer.process();
   }


   private final Pyramid _pyramid;
   private final File[]  _inputDirectories;
   private final File    _outputDirectory;
   private final float   _jpegQuality;


   TilesMixer(final Pyramid pyramid,
              final List<String> inputDirectoriesNames,
              final String outputDirectoryName,
              final float jpegQuality) throws IOException {
      _pyramid = pyramid;
      _inputDirectories = new File[inputDirectoriesNames.size()];
      for (int i = 0; i < inputDirectoriesNames.size(); i++) {
         final String inputDirectoryName = inputDirectoriesNames.get(i);
         final File inputDirectory = new File(inputDirectoryName);
         IOUtils.checkDirectory(inputDirectory);
         _inputDirectories[i] = inputDirectory;
      }

      _outputDirectory = new File(outputDirectoryName);
      IOUtils.ensureEmptyDirectory(_outputDirectory);

      _jpegQuality = jpegQuality;
   }


   private SourcePyramid[] getSourcePyramids() throws IOException {
      final int length = _inputDirectories.length;
      final SourcePyramid[] sourcePyramids = new SourcePyramid[length];
      for (int i = 0; i < length; i++) {
         final File inputDirectory = _inputDirectories[i];
         sourcePyramids[i] = new SourcePyramid(inputDirectory);
      }
      return sourcePyramids;
   }

   private static class DefaultThreadFactory
      implements
         ThreadFactory {
      private static final AtomicInteger poolNumber    = new AtomicInteger(1);

      private final ThreadGroup          _group;
      private final AtomicInteger        _threadNumber = new AtomicInteger(1);
      private final String               _namePrefix;
      private final int                  _threadPriority;


      DefaultThreadFactory(final int threadPriority) {
         final SecurityManager s = System.getSecurityManager();
         _group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
         _namePrefix = "pool-" + poolNumber.getAndIncrement() + "-thread-";
         _threadPriority = threadPriority;
      }


      @Override
      public Thread newThread(final Runnable runnable) {
         final Thread t = new Thread(_group, runnable, _namePrefix + _threadNumber.getAndIncrement(), 0);
         //         if (t.isDaemon()) {
         t.setDaemon(false);
         //         }
         //         if (t.getPriority() != _threadPriority) {
         t.setPriority(_threadPriority);
         //         }
         return t;
      }
   }


   private static ThreadFactory defaultThreadFactory(final int threadPriority) {
      return new DefaultThreadFactory(threadPriority);
   }


   private void process() throws IOException {
      final MergedPyramid mergedPyramid = new MergedPyramid(_pyramid, getSourcePyramids(), _jpegQuality);
      //mergedPyramid.merge(_outputDirectory);

      final int scaleFactor = 2;
      final int cpus = Runtime.getRuntime().availableProcessors();
      final int maxThreads = Math.max(cpus * scaleFactor, 1);

      final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, maxThreads, 10, TimeUnit.SECONDS,
               new SynchronousQueue<Runnable>(), defaultThreadFactory(Thread.NORM_PRIORITY));
      executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());


      final Object mutex = new Object();

      final long steps = mergedPyramid.getTilesCount();

      final Progress progress = new Progress(steps, 10, false) {
         @Override
         public void informProgress(final long stepsDone,
                                    final double percent,
                                    final long elapsed,
                                    final long estimatedMsToFinish) {
            Logger.log("Merging " + progressString(stepsDone, percent, elapsed, estimatedMsToFinish));
         }
      };

      mergedPyramid.process(_outputDirectory, progress, executor, mutex);

      executor.shutdown();
      try {
         executor.awaitTermination(2, TimeUnit.DAYS);
      }
      catch (final InterruptedException e) {
         throw new RuntimeException(e);
      }
      progress.finish();

      Logger.log("done!");
   }


   public static void main(final String[] args) throws IOException {
      System.out.println("TilesMixer 0.1");
      System.out.println("--------------\n");


      //      final String inputDirectoryName = "/Users/dgd/Desktop/LH-Imagery/_result_/";
      //      final String outputDirectoryName = "/Users/dgd/Desktop/LH-Imagery/_merged";

      //  TilesMixer.processDirectories(inputDirectoriesNames, outputDirectoryName);

      final String inputDirectoryName = "/Volumes/SSD1/_TEST_/mercator_TrueMarble.1km/";
      final String outputDirectoryName = "/Volumes/SSD1/_TEST_/mercator_TrueMarble.1km_merged/";
      final Pyramid pyramid = WebMercatorPyramid.createDefault();
      final float jpegQuality = 0.9f;

      TilesMixer.processSubdirectories(pyramid, inputDirectoryName, outputDirectoryName, jpegQuality);
   }


}
