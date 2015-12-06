

package com.glob3mobile.tools.tiling.pyramid;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.glob3mobile.geo.GEOSector;
import com.glob3mobile.utils.IOUtils;
import com.glob3mobile.utils.Progress;


public class MergedPyramid {


   private class MergedTile {
      private final MergedColumn            _column;
      private final int                     _row;
      private final List<SourcePyramidTile> _sourceTiles = new ArrayList<>();


      private MergedTile(final MergedColumn column,
                         final int row) {
         _column = column;
         _row = row;
      }


      private void addSourceTile(final SourcePyramidTile sourceTile) {
         _sourceTiles.add(sourceTile);
      }


      private BufferedImage createImage(final List<File> sourceImageFiles) throws IOException {
         if (sourceImageFiles.isEmpty()) {
            return null;
         }
         else if (sourceImageFiles.size() == 1) {
            return ImageIO.read(sourceImageFiles.get(0));
         }
         else {
            BufferedImage image = null;
            Graphics2D g2d = null;
            for (final File sourceImageFile : sourceImageFiles) {

               final BufferedImage sourceImage = ImageIO.read(sourceImageFile);
               if (g2d == null) {
                  image = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                  g2d = image.createGraphics();
               }

               g2d.drawImage(sourceImage, 0, 0, null);
            }
            if (g2d != null) {
               g2d.dispose();
            }

            return image;
         }
      }


      private String getRelativeFileName() {
         return _column.getRelativeFileName() + "/" + _row + ".jpg";
      }


      private void process(final SourcePyramid[] sourcePyramids,
                           final File outputDirectory,
                           final Object mutex) throws IOException {
         final File output = new File(outputDirectory, getRelativeFileName());

         Collections.sort( //
                  _sourceTiles, //
                  new Comparator<SourcePyramidTile>() {
                     @Override
                     public int compare(final SourcePyramidTile o1,
                                        final SourcePyramidTile o2) {
                        return Integer.compare(o1.getPyramidMaxLevel(), o2.getPyramidMaxLevel());
                     }
                  });

         // all sourcePyramids contributed to the tile, just mix the images
         if (_sourceTiles.size() == sourcePyramids.length) {
            mergeFromSourceTiles(output, mutex);
         }
         else {
            final List<SourcePyramidTile> ancestors = new ArrayList<>();
            for (final SourcePyramid sourcePyramid : sourcePyramids) {
               if (!sourcePyramidContributed(sourcePyramid)) {
                  final SourcePyramidTile ancestor = sourcePyramid.getBestAncestor(_column._level._level, _column._column, _row);
                  if (ancestor != null) {
                     ancestors.add(ancestor);
                  }
               }
            }

            if (ancestors.isEmpty()) {
               // no ancestors for this tile
               mergeFromSourceTiles(output, mutex);
            }
            else if ((_sourceTiles.size() == 1) && isFullOpaque(ImageIO.read(_sourceTiles.get(0).getImageFile()))) {
               mergeFromSourceTiles(output, mutex);
            }
            else {
               //               Logger.log("Found ancestors for " + _column._level._level + "/" + _column._column + "/" + _row);
               //               for (final SourcePyramidTile ancestor : ancestors) {
               //                  Logger.log("  Ancestor " + ancestor.getImageFile());
               //               }

               final Comparator<SourcePyramidTile> comparator = new Comparator<SourcePyramidTile>() {
                  @Override
                  public int compare(final SourcePyramidTile o1,
                                     final SourcePyramidTile o2) {
                     return Integer.compare(o1.getPyramidMaxLevel(), o2.getPyramidMaxLevel());
                  }
               };
               Collections.sort(ancestors, comparator);
               mergeFromSourceTilesAndAncestors(ancestors, output, mutex);
            }
         }
      }


      private void mergeFromSourceTilesAndAncestors(final List<SourcePyramidTile> ancestors,
                                                    final File output,
                                                    final Object mutex) throws IOException {
         //Logger.log("    Merging tile \"" + output.getAbsolutePath() + "\"");


         final List<BufferedImage> sourceImageFiles = _sourceTiles.stream().map(sourceTile -> {
            try {
               return ImageIO.read(sourceTile.getImageFile());
            }
            catch (final IOException e) {
               throw new RuntimeException(e);
            }
         }).collect(Collectors.toList());

         final BufferedImage firstImage = sourceImageFiles.get(0);

         final BufferedImage image = new BufferedImage(firstImage.getWidth(), firstImage.getHeight(),
                  BufferedImage.TYPE_4BYTE_ABGR);

         final Graphics2D g2d = image.createGraphics();
         g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

         final GEOSector tileSector = _pyramid.sectorFor(_column._level._level, _column._column, _row);

         for (final SourcePyramidTile ancestor : ancestors) {
            final BufferedImage ancestorImage = ImageIO.read(ancestor.getImageFile());

            final GEOSector ancestorSector = _pyramid.sectorFor(ancestor._column._level._level, ancestor._column._column,
                     ancestor._row);

            final Point2D lowerUV = ancestorSector.getUVCoordinates(tileSector._lower);
            final Point2D upperUV = ancestorSector.getUVCoordinates(tileSector._upper);

            final int ancestorImageWidth = ancestorImage.getWidth();
            final int ancestorImageHeight = ancestorImage.getHeight();

            final int dx1 = 0;
            final int dy1 = 0;
            final int dx2 = 256;
            final int dy2 = 256;
            final int sx1 = (int) Math.round(lowerUV.getX() * ancestorImageWidth);
            final int sy2 = (int) Math.round(lowerUV.getY() * ancestorImageHeight);
            final int sx2 = (int) Math.round(upperUV.getX() * ancestorImageWidth);
            final int sy1 = (int) Math.round(upperUV.getY() * ancestorImageHeight);
            g2d.drawImage(ancestorImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);

            // g2d.drawImage(ancestorImage, 0, 0, null);
         }

         for (final BufferedImage sourceImage : sourceImageFiles) {
            g2d.drawImage(sourceImage, 0, 0, null);
         }


         //         g2d.setPaint(Color.YELLOW);
         //         g2d.drawString("lower=" + tileSector._lower, 2, 250 - 18);
         //         g2d.drawString("upper=" + tileSector._upper, 2, 250);

         g2d.dispose();

         saveImage(output, image, mutex);
      }


      private void mergeFromSourceTiles(final File output,
                                        final Object mutex) throws IOException {
         //Logger.log("    Merging tile \"" + output.getAbsolutePath() + "\"");

         final List<File> sourceImageFiles = //
         _sourceTiles.stream() //
         .map(sourceTile -> sourceTile.getImageFile()) //
         .collect(Collectors.toList());

         saveImage(output, createImage(sourceImageFiles), mutex);
      }


      private boolean sourcePyramidContributed(final SourcePyramid sourcePyramid) {
         for (final SourcePyramidTile sourceTile : _sourceTiles) {
            if (sourceTile._column._level._pyramid == sourcePyramid) {
               return true;
            }
         }
         return false;
      }
   }


   private class MergedColumn {
      private final MergedLevel              _level;
      private final int                      _column;
      private final Map<Integer, MergedTile> _tiles = new HashMap<>();


      MergedColumn(final MergedLevel level,
                   final int column) {
         _level = level;
         _column = column;
      }


      private void addSourceTile(final SourcePyramidTile sourceTile) {
         final Integer rowKey = sourceTile._row;
         MergedTile current = _tiles.get(rowKey);
         if (current == null) {
            current = new MergedTile(this, rowKey);
            _tiles.put(rowKey, current);
         }
         current.addSourceTile(sourceTile);
      }


      private void process(final SourcePyramid[] sourcePyramids,
                           final File outputDirectory,
                           final Progress progress,
                           final ExecutorService executor,
                           final Object mutex) {
         //Logger.log("  Processing column " + _level._level + "/" + _column);

         final List<Integer> keys = new ArrayList<>(_tiles.keySet());
         Collections.sort(keys);

         for (final Integer key : keys) {
            final MergedTile tile = _tiles.get(key);

            executor.execute(new Runnable() {
               @Override
               public void run() {
                  try {
                     tile.process(sourcePyramids, outputDirectory, mutex);
                     progress.stepDone();
                  }
                  catch (final IOException e) {
                     e.printStackTrace();
                  }
               }
            });
            // tile.process(sourcePyramids, outputDirectory);
            //progress.stepDone();
         }
      }


      private String getRelativeFileName() {
         return _level.getRelativeFileName() + "/" + _column;
      }


      private long getTilesCount() {
         return _tiles.size();
      }


   }


   private class MergedLevel {
      private final int                        _level;
      private final Map<Integer, MergedColumn> _columns = new HashMap<>();


      private MergedLevel(final int level) {
         _level = level;
      }


      public void addSourceTile(final SourcePyramidColumn sourceColumn,
                                final SourcePyramidTile sourceTile) {
         final Integer columnKey = sourceColumn._column;
         MergedColumn current = _columns.get(columnKey);
         if (current == null) {
            current = new MergedColumn(this, columnKey);
            _columns.put(columnKey, current);
         }
         current.addSourceTile(sourceTile);
      }


      private void process(final SourcePyramid[] sourcePyramids,
                           final File outputDirectory,
                           final Progress progress,
                           final ExecutorService executor,
                           final Object mutex) {
         //Logger.log("Processing level " + _level);

         final List<Integer> keys = new ArrayList<>(_columns.keySet());
         Collections.sort(keys);

         for (final Integer key : keys) {
            final MergedColumn column = _columns.get(key);
            column.process(sourcePyramids, outputDirectory, progress, executor, mutex);
         }
      }


      private String getRelativeFileName() {
         return Integer.toString(_level);
      }


      private long getTilesCount() {
         long tilesCount = 0;
         for (final MergedColumn column : _columns.values()) {
            tilesCount += column.getTilesCount();
         }
         return tilesCount;
      }

   }

   private final Pyramid                   _pyramid;
   private final SourcePyramid[]           _sourcePyramids;
   private final Map<Integer, MergedLevel> _levels = new HashMap<>();


   public MergedPyramid(final Pyramid pyramid,
                        final SourcePyramid[] sourcePyramids) {
      _pyramid = pyramid;
      _sourcePyramids = sourcePyramids;

      for (final SourcePyramid sourcePyramid : _sourcePyramids) {
         for (final SourcePyramidLevel sourceLevel : sourcePyramid.getLevels()) {
            for (final SourcePyramidColumn sourceColumn : sourceLevel.getColumns()) {
               for (final SourcePyramidTile sourceTile : sourceColumn.getTiles()) {
                  addSourceTile(sourceLevel, sourceColumn, sourceTile);
               }
            }
         }
      }
   }


   private void addSourceTile(final SourcePyramidLevel sourceLevel,
                              final SourcePyramidColumn sourceColumn,
                              final SourcePyramidTile sourceTile) {
      final Integer levelKey = sourceLevel._level;
      MergedLevel current = _levels.get(levelKey);
      if (current == null) {
         current = new MergedLevel(levelKey);
         _levels.put(levelKey, current);
      }
      current.addSourceTile(sourceColumn, sourceTile);
   }


   public void process(final File outputDirectory,
                       final Progress progress,
                       final ExecutorService executor,
                       final Object mutex) {
      final List<Integer> keys = new ArrayList<>(_levels.keySet());
      Collections.sort(keys);

      for (final Integer key : keys) {
         //final int __REMOVE;
         //if (key.intValue() <= 10) {
         final MergedLevel level = _levels.get(key);
         level.process(_sourcePyramids, outputDirectory, progress, executor, mutex);
         //}
      }
   }


   private static void saveImage(final File output,
                                 final BufferedImage image,
                                 final Object mutex) throws IOException {
      synchronized (mutex) {
         final File directory = output.getParentFile();
         if (!directory.exists()) {
            if (!directory.mkdirs()) {
               throw new IOException("Can't create directory \"" + directory.getAbsolutePath() + "\"");
            }
         }
      }

      final BufferedImage imageRGB = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
      final Graphics2D g2d = imageRGB.createGraphics();
      g2d.drawImage(image, 0, 0, null);
      g2d.dispose();

      IOUtils.writeJPEG(imageRGB, output, 0.9f);
   }


   private static boolean isFullOpaque(final BufferedImage image) {
      final int width = image.getWidth();
      final int height = image.getHeight();
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            final int rbg = image.getRGB(x, y);
            final int alpha = (rbg >> 24) & 0xff;
            if (alpha < 255) {
               return false;
            }
         }
      }
      return true;
   }


   public long getTilesCount() {
      long tilesCount = 0;
      for (final MergedLevel level : _levels.values()) {
         tilesCount += level.getTilesCount();
      }
      return tilesCount;
   }


}
