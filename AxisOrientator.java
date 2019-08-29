package ignalau.appauto;


import android.util.Log;
import android.widget.TextView;

import org.ejml.data.MatrixType;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.SingularOps_DDRM;
import org.ejml.simple.SimpleMatrix;

import static ignalau.appauto.Constants.MIN_MARGINAL_ERROR;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

/**
 *
 * @author Ignacio Lauret <ignalauret@gmail.com>
 *
 */

class AxisOrientator {


    /**
     * I receive a data Matrix (already centered) and return the direction vector that points to the
     * most increasing direction of the Data.
     * This relies on the use of my first column in the U matrix from my SV Decomposition of the data Matrix.
     */
    public static SimpleMatrix calcularSVD(SimpleMatrix dataMatrix, String axis) {

        SimpleMatrix returnAxis = new SimpleMatrix(3, 1, MatrixType.DDRM);

        int range = SingularOps_DDRM.rank(dataMatrix.getDDRM());
        double condition = dataMatrix.conditionP2();

        /* Checks if my matrix's Range and Condition satisfy the SVD conditions. */
        if (range > 1 && condition < 100) {

            /* Gets the U matrix from the SV Decomposition and return the first column. */
            SimpleMatrix svdMatrix_U = dataMatrix.svd(false).getU();
            CommonOps_DDRM.extractColumn(svdMatrix_U.getDDRM(), 0, returnAxis.getDDRM());

            printAxis(returnAxis, axis + "_axis");
            return returnAxis;

        } else {
            /*If I don't satisfy SVD conditions, I return an error.
              TODO: Por implementar... */
            Log.d("Error", "SVD conditions unsatisfied");
            Log.d("Error", "Range: "+ Integer.toString(range));
            Log.d("Error", "Condition: " + Double.toString(condition));

            return null;
        }
    }


    /* Calculates the axis by making SV Decompositions onto fragments of the data, just taking the ones
    that pass the leave one Out error test. */
    static SimpleMatrix complexSVD(SimpleMatrix data, String tag){

        /* Non-Null validation */
        if(data == null) return null;

        SimpleMatrix returnAxis = new SimpleMatrix(3,1,MatrixType.DDRM);

        /* Gets the 4 SVD calculations */
        SimpleMatrix x_Axis1 = calcularSVD(data.cols(0,100),tag+"_1");
        SimpleMatrix x_Axis2 = calcularSVD(data.cols(50,150),tag+"_2");
        SimpleMatrix x_Axis3 = calcularSVD(data.cols(100,200),tag+"_3");
        SimpleMatrix x_Axis4 = calcularSVD(data.cols(0,200),tag+"_full");

        /* Calculates the 4 errors */
        double error_1 = leaveOneOut(x_Axis1,x_Axis2,x_Axis3,x_Axis4);
        double error_2 = leaveOneOut(x_Axis2,x_Axis1,x_Axis3,x_Axis4);
        double error_3 = leaveOneOut(x_Axis3,x_Axis2,x_Axis1,x_Axis4);
        double error_4 = leaveOneOut(x_Axis4,x_Axis2,x_Axis3,x_Axis1);

        double scaleCounter = 0;

        /* If the error is less than the tolerance, then it adds the vector */
        if(error_1 < MIN_MARGINAL_ERROR){
            CommonOps_DDRM.add(returnAxis.getDDRM(),x_Axis1.getDDRM(),returnAxis.getDDRM());
            scaleCounter++;
        }
        if(error_2 < MIN_MARGINAL_ERROR){
            CommonOps_DDRM.add(returnAxis.getDDRM(),x_Axis2.getDDRM(),returnAxis.getDDRM());
            scaleCounter++;
        }
        if(error_3 < MIN_MARGINAL_ERROR || scaleCounter == 0){
            CommonOps_DDRM.add(returnAxis.getDDRM(),x_Axis3.getDDRM(),returnAxis.getDDRM());
            scaleCounter++;
        }
        if(error_4 < MIN_MARGINAL_ERROR || scaleCounter == 1){
            CommonOps_DDRM.add(returnAxis.getDDRM(),x_Axis4.getDDRM(),returnAxis.getDDRM());
            scaleCounter++;
        }

        /* Calculates the average */
        returnAxis = returnAxis.scale(1/scaleCounter);

        AxisOrientator.printAxis(returnAxis,"Final_X");

        return returnAxis;
    }

    /* Compares the $observed vector with the average of the other 4 and returns the error value */
    private static double leaveOneOut(SimpleMatrix observed, SimpleMatrix x1, SimpleMatrix x2, SimpleMatrix x3){
        double ret;
        SimpleMatrix averageMatrix = getAverageAxis(x1.concatColumns(x2,x3),"X_average");
        ret = CommonOps_DDRM.elementSumAbs(observed.minus(averageMatrix).getDDRM());
        return ret;
    }


    /* If I already got two axis, I can calculate the third one by doing the cross product of the first two,
       relying on the fact that they are orthogonal vectors. */
    static SimpleMatrix getThirdAxis(SimpleMatrix axis1, SimpleMatrix axis2, String tag) {

        SimpleMatrix returnAxis;

        returnAxis = crossProduct(axis1, axis2);

        normalize(returnAxis);
        printAxis(returnAxis,tag + "_axis");
        return returnAxis;

    }

    /* If I've got constant data it gets the vector where that data is pointing to. */
    static SimpleMatrix getAverageAxis(SimpleMatrix dataMatrix,String tag){

        SimpleMatrix returnAxis = DataAnalizer.average(dataMatrix);
        normalize(returnAxis);
        printAxis(returnAxis,tag + "_axis");
        return returnAxis;

    }


    /* Makes the matrix's norm = 1. */
    static void normalize(SimpleMatrix matrix){

        matrix.set(matrix.divide(matrix.normF()));
    }


    /* My implementation of the Cross Product. */
    private static SimpleMatrix crossProduct(SimpleMatrix m1, SimpleMatrix m2){

        SimpleMatrix returnAxis = new SimpleMatrix(3,1,MatrixType.DDRM);

        double[] x = m1.getDDRM().getData();
        double[] z = m2.getDDRM().getData();

        returnAxis.setColumn(0,0, x[1]*z[2]-x[2]*z[1],x[0]*z[2]*(-1)+x[2]*z[0],x[0]*z[1]-x[1]*z[0]);
        return returnAxis;

    }

    /* Shows in terminal the given 3-Dimensional Vector. */
    static void printAxis(SimpleMatrix axis,String tag){

        Log.d(tag,"[" + Double.toString(axis.get(0)) + ","+Double.toString(axis.get(1))+","+Double.toString(axis.get(2))+"]");

    }

    /* Displays the axis on a TextView */
    static void printAxisOnView(TextView text, String textPrefix , SimpleMatrix axis){

        String axisString = "[" + Double.toString(axis.get(0)).substring(0,4) + ","+Double.toString(axis.get(1)).substring(0,4)+","+Double.toString(axis.get(2)).substring(0,4)+"]";
        text.setText(textPrefix + axisString);

    }

    static SimpleMatrix getSigmas(SimpleMatrix means, SimpleMatrix data){
        SimpleMatrix res = new SimpleMatrix(3,1,MatrixType.DDRM);

        res.set(0,getSigma(means.get(0),data.rows(0,1)));
        res.set(1,getSigma(means.get(1),data.rows(1,2)));
        res.set(2,getSigma(means.get(2),data.rows(2,3)));

        return res;
    }

    static double getSigma(double mean, SimpleMatrix row){
        double sigma = 0.0;
        int cols = row.numCols();
        Log.d("Cols",Integer.toString(cols));
        for(int i = 0; i < cols; i++){
            sigma += pow(mean - row.get(i), 2.0);
        }
        Log.d("Sigma",Double.toString(sigma));
        sigma = sqrt(sigma/cols);
        Log.d("Sigma After",Double.toString(sigma));
        return sigma*10;
    }

    static boolean compareNewMean(SimpleMatrix newMean, SimpleMatrix historicMean, SimpleMatrix historicSigma){
        SimpleMatrix upperBound = historicMean.plus(historicSigma);
        SimpleMatrix lowerBound = historicMean.minus(historicSigma);

        for(int i = 0; i < 3; i++){
            if(newMean.get(i) > upperBound.get(i) || newMean.get(i) < lowerBound.get(i)) return true;
        }

        return false;
    }

}
