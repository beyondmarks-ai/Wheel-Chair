package org.wheelchair.blink;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContourGeometryTest {
    @Test public void qvgaFeaturesCanBeUsableWithoutA200PixelFaceBox(){
        var features=face(24,0);
        ContourGeometry.requireVisible(320,240,features);
        assertEquals(.4,measure(features).mouth(),.0001);
        features[5][1]=new ContourGeometry.Point(120,240);
        assertThrows(IllegalArgumentException.class,()->ContourGeometry.requireVisible(320,240,features));
    }
    private static ContourGeometry.Point[] line(double cx,double y,double width) {
        return new ContourGeometry.Point[]{new ContourGeometry.Point(cx-width/2,y),
                new ContourGeometry.Point(cx,y),new ContourGeometry.Point(cx+width/2,y)};
    }
    private static ContourGeometry.Point[][] face(double mouth,double brow) {
        return new ContourGeometry.Point[][]{line(70,100,30),line(170,100,30),
                line(70,80-brow,30),line(170,80-brow,30),line(120,160,60),line(120,160+mouth,60)};
    }
    private static ContourGeometry.Features measure(ContourGeometry.Point[][] f) {
        return ContourGeometry.measure(f[0],f[1],f[2],f[3],f[4],f[5]);
    }
    @Test public void openingAndBrowLiftAreIndependent() {
        var neutral=measure(face(0,0));var mouth=measure(face(24,0));var brows=measure(face(0,6));
        assertEquals(0,neutral.mouth(),.0001);assertEquals(.4,mouth.mouth(),.0001);
        assertEquals(neutral.leftBrow(),mouth.leftBrow(),.0001);
        assertEquals(0,brows.mouth(),.0001);assertEquals(.06,brows.leftBrow()-neutral.leftBrow(),.0001);
    }
    @Test public void scaleTranslationRollAndAnatomicalLabelsDoNotChangeRatios() {
        var f=face(24,6);var expected=measure(f);double angle=.3;
        for(int i=0;i<f.length;i++)for(int j=0;j<f[i].length;j++) {
            var p=f[i][j];f[i][j]=new ContourGeometry.Point(30+2*(p.x()*Math.cos(angle)-p.y()*Math.sin(angle)),
                    70+2*(p.x()*Math.sin(angle)+p.y()*Math.cos(angle)));
        }
        var actual=measure(f);assertEquals(expected.mouth(),actual.mouth(),.00001);
        assertEquals(expected.leftBrow(),actual.leftBrow(),.00001);
        var eye=f[0];f[0]=f[1];f[1]=eye;var brow=f[2];f[2]=f[3];f[3]=brow;
        assertEquals(expected.mouth(),measure(f).mouth(),.00001);
        assertEquals(expected.leftBrow(),measure(f).leftBrow(),.00001);
    }
    @Test public void unusableContoursAreRejected() {
        var f=face(0,0);f[0]=null;
        assertThrows(IllegalArgumentException.class,()->measure(f));
        var small=face(0,0);small[1]=line(80,100,30);
        assertThrows(IllegalArgumentException.class,()->measure(small));
        var missing=face(0,0);missing[5][1]=new ContourGeometry.Point(Double.NaN,160);
        assertThrows(IllegalArgumentException.class,()->measure(missing));
        var noMiddle=face(0,0);noMiddle[4]=new ContourGeometry.Point[]{
                new ContourGeometry.Point(90,160),new ContourGeometry.Point(91,160),new ContourGeometry.Point(150,160)};
        assertThrows(IllegalArgumentException.class,()->measure(noMiddle));
    }
    @Test public void separateCalibrationNormalizesSmallBrowLiftsWithoutChangingEyeScoresToOpen() {
        double[] mp={.1,.1,.1,.1,.1,.1,.1,.1};
        double[] rest=measure(face(0,0)).scores(mp);
        var profile=new FaceCalibration.Profile(0,.4,rest);
        double[] raised=measure(face(0,6)).scores(mp);
        var normalized=ContourGeometry.normalize(profile,0,raised);
        assertTrue(normalized[5]>.55);assertTrue(normalized[6]>.35);assertTrue(normalized[7]>.35);
        assertEquals(0,normalized[4],0);
        raised[0]=raised[1]=.8;
        assertTrue(ContourGeometry.normalize(profile,.4,raised)[0]>=.5);
        assertTrue(Double.isNaN(ContourGeometry.normalize(profile,Double.NaN,raised)[4]));
        assertNotEquals(ContourGeometry.profileKey(false),ContourGeometry.profileKey(true));
        assertEquals("faceCalibration",ContourGeometry.profileKey(false));
    }
}
