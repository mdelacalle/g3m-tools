

package com.glob3mobile.tools.tiling.pyramid;

import java.io.File;


class SourcePyramidTile {
   public final int          _row;
   final SourcePyramidColumn _column;
   private final File        _imageFile;


   //      private final boolean _isFullOpaque;


   SourcePyramidTile(final SourcePyramidColumn column,
                     final File imageFile) {
      final String nameSansExtension = removeExtension(imageFile.getName());
      _row = Integer.parseInt(nameSansExtension);
      _column = column;
      _imageFile = imageFile;
      //         _isFullOpaque = initializeIsOpaque();
   }


   private static String removeExtension(final String name) {
      return name.substring(0, name.length() - ".png".length());
   }


   //      private boolean initializeIsOpaque() throws IOException {
   //         final BufferedImage image = ImageIO.read(_imageFile);
   //         return isFullOpaque(image);
   //      }


   @Override
   public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("[SourcePyramidTile ");
      builder.append(_imageFile.getName());
      builder.append("]");
      return builder.toString();
   }


   public File getImageFile() {
      return _imageFile;
   }


   public int getPyramidMaxLevel() {
      return _column._level._pyramid.getMaxLevel();
   }

}
