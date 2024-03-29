

package com.glob3mobile.tools.tiling.pyramid;

import static java.lang.Math.PI;
import static java.lang.Math.atan;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.geotools.coverage.grid.GridCoverage2D;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.glob3mobile.geo.GEOSector;


public class WebMercatorPyramid
   extends
      Pyramid {

   private static final double UPPER_LIMIT_DEGREES = 85.0511287798;
   private static final double LOWER_LIMIT_DEGREES = -85.0511287798;


   public static Pyramid createDefault() {
      final int tileImageWidth = 256;
      final int tileImageHeight = 256;
      return new WebMercatorPyramid(tileImageWidth, tileImageHeight);
   }


   private final int        _tileImageWidth;
   private final int        _tileImageHeight;
   private final Tile       _topTile;
   private final List<Tile> _topTiles;


   public WebMercatorPyramid(final int tileImageWidth,
                             final int tileImageHeight) {
      _tileImageWidth = tileImageWidth;
      _tileImageHeight = tileImageHeight;
      _topTile = createTopTile();
      _topTiles = Collections.unmodifiableList(Arrays.asList(_topTile));
   }


   private Tile createTopTile() {
      final Tile parent = null;
      final GEOSector sector = GEOSector.fullSphere();
      final int level = 0;
      final int row = 0;
      final int column = 0;

      return new Tile(parent, sector, level, row, column);
   }


   @Override
   public int getTileImageWidth() {
      return _tileImageWidth;
   }


   @Override
   public int getTileImageHeight() {
      return _tileImageHeight;
   }


   @Override
   public List<Tile> getTopTiles() {
      return _topTiles;
   }


   @Override
   public int getNumberOfRows(final int level) {
      return (int) pow(2, level);
   }


   @Override
   public List<Tile> createChildren(final GEOSector sector,
                                    final Tile tile) {
      final GEOSector tileSector = tile._sector;
      final double splitLatitude = calculateSplitLatitudeDegrees(tileSector._lower._latitude, tileSector._upper._latitude);
      final double splitLongitude = tileSector._center._longitude;
      return tile.createSubTiles(sector, splitLatitude, splitLongitude);
   }


   private static double calculateSplitLatitudeDegrees(final double lowerLatitudeDegrees,
                                                       final double upperLatitudeDegrees) {
      final double middleV = (getMercatorV(lowerLatitudeDegrees) + getMercatorV(upperLatitudeDegrees)) / 2;
      return toLatitudeDegrees(middleV);
   }


   private static double getMercatorV(final double latitudeDegrees) {
      if (latitudeDegrees >= UPPER_LIMIT_DEGREES) {
         return 0;
      }
      if (latitudeDegrees <= LOWER_LIMIT_DEGREES) {
         return 1;
      }

      final double pi4 = PI * 4;
      final double latSin = sin(toRadians(latitudeDegrees));
      return 1.0 - ((log((1.0 + latSin) / (1.0 - latSin)) / pi4) + 0.5);
   }


   private static double toLatitudeDegrees(final double v) {
      final double exp = exp(-2 * PI * (1.0 - v - 0.5));
      final double atan = atan(exp);
      return toDegrees((PI / 2) - (2 * atan));
   }


   @Override
   public void checkCRS(final GridCoverage2D coverage) {
      final CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem();

      if (!crs.getName().getCode().equalsIgnoreCase("WGS 84 / Pseudo-Mercator")) {
         throw new RuntimeException("Invalid CRS\n" + crs);
      }
   }


   @Override
   public Point2D resolutionForLevel(final int level) {
      final int splitsByLatitude = (int) Math.pow(2, level);
      final int splitsByLongitude = (int) Math.pow(2, level);

      final double deltaLatitude = 180.0 / splitsByLatitude;
      final double deltaLongitude = 360.0 / splitsByLongitude;

      final double x = deltaLongitude / _tileImageWidth;
      final double y = deltaLatitude / _tileImageHeight;
      return new Point2D.Double(x, y);
   }


   @Override
   public GEOSector sectorFor(final int level,
                              final int column,
                              final int row) {
      final Tile tile = getTile(level, column, row);
      return (tile == null) ? null : tile._sector;
   }


   public Tile getTile(final int level,
                       final int column,
                       final int row) {
      return getTile(_topTile, level, column, row);
   }


   private Tile getTile(final Tile tile,
                        final int level,
                        final int column,
                        final int row) {
      final int tileLevel = tile._level;
      if (level < tileLevel) {
         return null;
      }
      else if (level == tileLevel) {
         return (tile._column == column) && (tile._row == row) ? tile : null;
      }
      else {
         final int deltaLevel2 = (int) pow(2, (level - tileLevel));
         final int rowAtCurrentLevel = row / deltaLevel2;
         final int columnAtCurrentLevel = column / deltaLevel2;

         if ((rowAtCurrentLevel == tile._row) && (columnAtCurrentLevel == tile._column)) {
            final List<Tile> children = createChildren(GEOSector.fullSphere(), tile);
            for (final Tile child : children) {
               final Tile candidate = getTile(child, level, column, row);
               if (candidate != null) {
                  return candidate;
               }
            }
         }
         return null;
      }
   }


   public static void main(final String[] args) {
      final int level = 4;
      final int column = 8;
      final int row = 0;

      final WebMercatorPyramid pyramid = (WebMercatorPyramid) WebMercatorPyramid.createDefault();
      final Tile tile = pyramid.getTile(level, column, row);
      System.out.println(tile);
   }


}
