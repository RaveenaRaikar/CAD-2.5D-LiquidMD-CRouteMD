package place.mathtools;

import java.util.List;

/*
 * Solves a linear system using the conjugate gradient method
 * Uses a Jacobi preconditioner
 */
public class CGSolver {

    private final List<Double> val;
    private final List<Integer> columnIndexes, rowPointers;
    private final double[] vector;

    public CGSolver(Csr crs, double[] vector) {
        this.val = crs.getVal();
        this.columnIndexes = crs.getColumnIndexes();
        this.rowPointers = crs.getRowPointers();
        this.vector = vector;
    }

    public double[] solve(double epsilon) {
        int dimensions = this.vector.length;

        double deltaNew;
        double deltaFirst;
        double deltaOld;
        double alpha;
        double beta;
        double temp;

        // Initialize everything
        double[] x = new double[dimensions];
        double[] r = new double[dimensions];
        for(int i = 0; i < dimensions; i++) {
            x[i] = 0.0;
            r[i] = this.vector[i];
        }

        double[] m = constructJacobi();

        double[] s = new double[dimensions];
        elementWiseProduct(m, r, s);

        double[] d = new double[dimensions];
        for(int i = 0; i < dimensions; i++) {
            d[i] = s[i];
        }

        deltaNew = dotProduct(s, r);
        deltaFirst = deltaNew;

        // Main loop of the algorithm
        double[] q = new double[dimensions];
        while(deltaNew > epsilon * epsilon * deltaFirst) {
            sparseMatrixVectorProduct(d, q);
            temp = dotProduct(d, q);
            alpha = deltaNew / temp;

            vectorUpdate(x, d, alpha, x);
            vectorUpdate(r, q, -alpha, r);
            elementWiseProduct(m, r, s);

            deltaOld = deltaNew;
            deltaNew = dotProduct(s, r);
            beta = deltaNew / deltaOld;

            vectorUpdate(s, d, beta, d);
        }

        return x;
    }

    private double[] constructJacobi() {
        int dimension = this.vector.length;
        double[] jacobi = new double[dimension];
        int index;

        for(int row = 0; row < dimension; row++) {
            index = this.rowPointers.get(row);
            // We suppose the diagonal elements are always non-zero
            while(this.columnIndexes.get(index) != row) {
                index++;
            }

            jacobi[row] = 1.0 / this.val.get(index);
        }
        return jacobi;
    }

    private void elementWiseProduct(double[] a, double[] b, double[] result) {
        for(int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }
    }

    private double dotProduct(double[] a, double[] b) {
        double sum = 0.0;

        for(int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    private void vectorUpdate(double[] a, double[] b, double constant, double[] result) {
        for(int i = 0; i < a.length; i++) {
            result[i] = a[i] + constant * b[i];
        }
    }

    private void sparseMatrixVectorProduct(double[] vector, double[] result) {
        int index = 0;
        for(int row = 0; row < this.vector.length; row++) {
            double sum = 0.0;
            int nextRow = row + 1;
            int maxIndex = this.rowPointers.get(nextRow);
            while(index < maxIndex) {
                sum += this.val.get(index) * vector[this.columnIndexes.get(index)];
                index++;
            }
            result[row] = sum;
        }
    }

}
