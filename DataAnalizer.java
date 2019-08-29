package ignalau.appauto;




import android.app.Activity;

import org.ejml.data.MatrixType;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import org.gitia.froog.Feedforward;
import org.gitia.froog.statistics.Compite;

import static ignalau.appauto.Constants.MIN_ACCELERATION_VALUE;
import static ignalau.appauto.Constants.NN_OUTPUT_1;
import static ignalau.appauto.Constants.NN_OUTPUT_2;
import static ignalau.appauto.Constants.NN_OUTPUT_3;
import static ignalau.appauto.Constants.NN_OUTPUT_4;
import static ignalau.appauto.Constants.NOISE_VALUE;
import static ignalau.appauto.Constants.ORTHOGONAL_MARGIN;
import static ignalau.appauto.MainScreen.dMinAcceleration;


/**
 *
 * @author Ignacio Lauret <ignalauret@gmail.com>
 *
 */

class DataAnalizer {



    /* Checks if the data doesn't have much variations, taking into account the noise that could be. */
    static boolean checkConstantData(SimpleMatrix dataMatrix) {

        /* Initialize the temp matrices for the calculations. */
        SimpleMatrix maxMatrix = new SimpleMatrix(3,1, MatrixType.DDRM);
        SimpleMatrix minMatrix = new SimpleMatrix(3,1,MatrixType.DDRM);
        SimpleMatrix dataRangeMatrix = new SimpleMatrix(3,1,MatrixType.DDRM);

        /* Takes the max and the min of each row (movement on each axis) and then subtracts them to get
           the range of variation in each axis. */
        CommonOps_DDRM.maxRows(dataMatrix.getDDRM(),maxMatrix.getDDRM());
        CommonOps_DDRM.minRows(dataMatrix.getDDRM(),minMatrix.getDDRM());
        CommonOps_DDRM.subtract(maxMatrix.getDDRM(),minMatrix.getDDRM(),dataRangeMatrix.getDDRM());

        /* Sums the absolute value of each axis variation range to get a number that describes the total variation. */
        double dataRange = CommonOps_DDRM.elementSumAbs(dataRangeMatrix.getDDRM());

        return(dataRange < NOISE_VALUE);

    }


    /* Modifies the data to reduce the noise in it, by making an average of a range of points near every point (Unused yet). */
    static SimpleMatrix maskData(SimpleMatrix matrix,int maskGrade){

        SimpleMatrix returnMatrix = matrix.copy();
        int cols = returnMatrix.numCols();
        int rows = returnMatrix.numRows();

        for(int i = 0;i<rows;i++) {
            for (int j = 0; j < cols - 2 * maskGrade - 1; j++) {

                returnMatrix.set(i, j + maskGrade,
                        matrix.rows(i, i + 1).cols(j, j + 2 * maskGrade + 2).elementSum()
                                / (2 * maskGrade + 1));

            }
        }
        return returnMatrix;

    }

    /* Subtracts a given vector on each column to center the data around it. */
    static SimpleMatrix centerData(SimpleMatrix dataMatrix,SimpleMatrix centerVector){

        SimpleMatrix dataMatrixCopy = new SimpleMatrix(3,200);
        dataMatrixCopy.set(dataMatrix);
        int dataSize = dataMatrix.numCols();

        for(int i=0;i<dataSize;i++){

            dataMatrixCopy.setColumn(i,0, dataMatrixCopy.cols(i,i+1).minus(centerVector).getDDRM().getData());
        }
        return dataMatrixCopy;
    }


    /* Makes the final pre-processing modifications to the data and feeds it into the Neural Network. */
    static String analyzeData(SimpleMatrix dataMatrix, Feedforward nNet, SimpleMatrix axisMatrix,
                              SimpleMatrix dataSaveMatrix, int dataSaveCounter, Activity activity){

        /* Center the data */
        SimpleMatrix centeredData = centerData(dataMatrix,
                axisMatrix.extractVector(false, 2).scale(9.8));

        /* Takes the data collector's axis and rotates the data to the real world axis. */
        SimpleMatrix orientedFinalData = axisMatrix.transpose().mult(centeredData);

        /* Saving data... */
        CommonOps_DDRM.insert(orientedFinalData.getDDRM(),dataSaveMatrix.getDDRM(),
                0,dataSaveCounter);

        /* Checks if there is constant, then assumes its idle. */
        if(checkConstantData(dataMatrix)){
            return("Idle");
        }

        /* Checks if there is a high acceleration on de X axis. */
        double average_X = average(orientedFinalData).get(0);

        if (average_X > dMinAcceleration) {
            return ("Acelerando");
        }

        double[] orientedDataArray = orientedFinalData.getDDRM().data;

        /* Adds gravity's acceleration to the Z-axis (The net was trained with data that had gravity). */
        int dataSize = dataMatrix.numCols();
        for(int i = 0;i<dataSize;i++){

            orientedDataArray[i+dataSize*2]+=9.8;

        }

        /* Feeds the data to the Neural Network. */
        SimpleMatrix a = new SimpleMatrix(3*dataSize,1, true,orientedDataArray);
        SimpleMatrix output = nNet.output(a).transpose();
        SimpleMatrix normalizedOutput = Compite.eval(output);

        NNResultPercentageFragment.updateNNResults(output,activity);

        return checkResult(normalizedOutput);
    }

    /* Returns the Tag of the given output from the NN. */
    private static String checkResult(SimpleMatrix s){
        if(s.get(0) == 1){

            return(NN_OUTPUT_1);

        } else if(s.get(1)==1){

            return(NN_OUTPUT_2);

        } else if(s.get(2)==1){

            return(NN_OUTPUT_3);

        } else{

            return(NN_OUTPUT_4);
        }
    }

    /* Checks if the axis is correctly orientated because SVD has two possible reults: correct axis and (correct axis) * -1
       The dataMatrix is already centered. */
    static void checkSVDOrientation(SimpleMatrix dataMatrix, SimpleMatrix axisMatrix){

        SimpleMatrix orientatedData = axisMatrix.mult(dataMatrix);

        double average_X = average(orientatedData).get(0);
        if(average_X > 0) return;
        axisMatrix.cols(0,1).scale(-1);
    }


    /* Checks if 2 vectors are orthogonal, and returns them corrected if not */
    static void checkIfOrthogonal(SimpleMatrix axis1, SimpleMatrix axis2){
        /* Computes the dot product */
        double dotProduct = axis1.dot(axis2);

        /* If < $ORTHOGONAL_MARGIN then they are considered orthogonal, return */
        if(dotProduct < ORTHOGONAL_MARGIN)return;

        AxisOrientator.printAxis(axis2, "Old X Axis: ");
        /* If not, projects axis2 onto the orthogonal plane of axis 1: v2 = v2-<v1,v2> *v1 */
        axis2.set(axis2.minus(axis1.scale(dotProduct)));
        AxisOrientator.normalize(axis2);

        AxisOrientator.printAxis(axis2,"New X Axis: ");
    }

    /* Returns the matrix = [averageX;averageY;averageZ] */
    static SimpleMatrix average(SimpleMatrix matrix){

        int cols = matrix.numCols();
        int rows = matrix.numRows();
        SimpleMatrix averageMatrix = new SimpleMatrix(rows,1,MatrixType.DDRM);

        for(int i = 0;i<rows;i++) {
            averageMatrix.set(i, CommonOps_DDRM.elementSum(matrix.rows(i, i + 1).getDDRM())/cols);
        }
        return averageMatrix;
    }

}
