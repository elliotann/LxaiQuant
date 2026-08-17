package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import smile.clustering.CentroidClustering;
import smile.clustering.KMeans;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MarketStateClusterService {

    private final MlProperties mlProperties;
    private volatile MarketStateCluster latestCluster;

    public MarketStateClusterService(MlProperties mlProperties) {
        this.mlProperties = mlProperties;
    }

    public MarketStateCluster cluster(BarSeries series) {
        int nClusters = mlProperties.getVolatility().getClustering().getNClusters();
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(0, endIndex - mlProperties.getVolatility().getLookbackDays());
        int n = endIndex - startIndex;

        if (n < nClusters * 5) {
            MarketStateCluster fallback = new MarketStateCluster();
            fallback.setRegime("UNKNOWN");
            fallback.setDescription("样本不足，无法聚类");
            return fallback;
        }

        double[][] data = new double[n][3];
        for (int i = startIndex, j = 0; i < endIndex; i++, j++) {
            double close = series.getBar(i).getClosePrice().doubleValue();
            double prevClose = i > startIndex ? series.getBar(i - 1).getClosePrice().doubleValue() : close;
            double returns = prevClose > 0 ? (close - prevClose) / prevClose : 0;
            double high = series.getBar(i).getHighPrice().doubleValue();
            double low = series.getBar(i).getLowPrice().doubleValue();
            double range = close > 0 ? (high - low) / close : 0;

            data[j][0] = returns;
            data[j][1] = range;
            data[j][2] = series.getBar(i).getVolume().doubleValue();
        }

        CentroidClustering<double[], double[]> kmeans = KMeans.fit(data, nClusters, 50);

        double[][] centroids = kmeans.centers();
        int[] assignments = kmeans.group();

        int[] clusterSizes = new int[nClusters];
        for (int a : assignments) clusterSizes[a]++;

        double[] clusterAvgReturn = new double[nClusters];
        double[] clusterAvgRange = new double[nClusters];
        for (int j = 0; j < n; j++) {
            int c = assignments[j];
            clusterAvgReturn[c] += data[j][0];
            clusterAvgRange[c] += data[j][1];
        }
        for (int c = 0; c < nClusters; c++) {
            if (clusterSizes[c] > 0) {
                clusterAvgReturn[c] /= clusterSizes[c];
                clusterAvgRange[c] /= clusterSizes[c];
            }
        }

        List<ClusterInfo> clusterInfos = new ArrayList<>();
        String[] labels = {"趋势上涨", "震荡整理", "趋势下跌"};
        String[] descriptions = {
                "涨幅较大，波动中等，适合持仓",
                "涨跌幅小，波动低，适合高抛低吸",
                "跌幅较大，波动高，建议观望或做空"
        };

        for (int c = 0; c < nClusters; c++) {
            ClusterInfo info = new ClusterInfo();
            info.setClusterId(c);
            info.setLabel(c < labels.length ? labels[c] : "状态" + c);
            info.setDescription(c < descriptions.length ? descriptions[c] : "");
            info.setCount(clusterSizes[c]);
            info.setAvgReturn(clusterAvgReturn[c]);
            info.setAvgRange(clusterAvgRange[c]);
            clusterInfos.add(info);
        }

        clusterInfos.sort((a, b) -> Double.compare(b.getAvgReturn(), a.getAvgReturn()));

        int lastCluster = assignments[n - 1];
        ClusterInfo currentCluster = clusterInfos.stream()
                .filter(ci -> ci.getClusterId() == lastCluster)
                .findFirst()
                .orElse(null);

        MarketStateCluster result = new MarketStateCluster();
        result.setClusters(clusterInfos);
        result.setCurrentCluster(currentCluster);
        result.setRegime(currentCluster != null ? currentCluster.getLabel() : "UNKNOWN");
        result.setDescription(currentCluster != null ? currentCluster.getDescription() : "");

        this.latestCluster = result;
        return result;
    }

    public MarketStateCluster getLatestCluster() {
        return latestCluster;
    }

    @Data
    public static class MarketStateCluster {
        private String regime;
        private String description;
        private List<ClusterInfo> clusters;
        private ClusterInfo currentCluster;
    }

    @Data
    public static class ClusterInfo {
        private int clusterId;
        private String label;
        private String description;
        private int count;
        private double avgReturn;
        private double avgRange;
    }
}
