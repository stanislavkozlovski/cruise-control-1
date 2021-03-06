/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol.model;

import com.linkedin.kafka.cruisecontrol.analyzer.AnalyzerUtils;
import com.linkedin.kafka.cruisecontrol.common.Resource;
import com.linkedin.kafka.cruisecontrol.analyzer.BalancingConstraint;
import com.linkedin.kafka.cruisecontrol.common.Statistic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import java.util.Set;
import java.util.function.Function;
import org.apache.kafka.common.TopicPartition;


public class ClusterModelStats {
  private final Map<Statistic, Map<Resource, Double>> _resourceUtilizationStats;
  private final Map<Statistic, Double> _potentialNwOutUtilizationStats;
  private final Map<Statistic, Number> _replicaStats;
  private final Map<Statistic, Number> _leaderReplicaStats;
  private final Map<Statistic, Number> _topicReplicaStats;
  private int _numBrokers;
  private int _numReplicasInCluster;
  private int _numPartitionsWithOfflineReplicas;
  private int _numTopics;
  private Map<Resource, Integer> _numBalancedBrokersByResource;
  private int _numBrokersUnderPotentialNwOut;
  private BalancingConstraint _balancingConstraint;
  private double[][] _utilizationMatrix;
  private int _numSnapshotWindows;
  private double _monitoredPartitionsPercentage;

  /**
   * Constructor for analysis stats.
   */
  ClusterModelStats() {
    _resourceUtilizationStats = new HashMap<>();
    _potentialNwOutUtilizationStats = new HashMap<>();
    _replicaStats = new HashMap<>();
    _leaderReplicaStats = new HashMap<>();
    _topicReplicaStats = new HashMap<>();
    _numBrokers = 0;
    _numReplicasInCluster = 0;
    _numPartitionsWithOfflineReplicas = 0;
    _numTopics = 0;
    _numBrokersUnderPotentialNwOut = 0;
    _numBalancedBrokersByResource = new HashMap<>();
  }

  /**
   * Populate the analysis stats with this cluster and given balancing constraint.
   *
   * @param clusterModel        The state of the cluster.
   * @param balancingConstraint Balancing constraint.
   * @return Analysis stats with this cluster and given balancing constraint.
   */
  ClusterModelStats populate(ClusterModel clusterModel, BalancingConstraint balancingConstraint) {
    _numBrokers = clusterModel.brokers().size();
    _numTopics = clusterModel.topics().size();
    _balancingConstraint = balancingConstraint;
    utilizationForResources(clusterModel);
    utilizationForPotentialNwOut(clusterModel);
    numForReplicas(clusterModel);
    numForLeaderReplicas(clusterModel);
    numForAvgTopicReplicas(clusterModel);
    _utilizationMatrix = clusterModel.utilizationMatrix();
    _numSnapshotWindows = clusterModel.load().numWindows();
    _monitoredPartitionsPercentage = clusterModel.monitoredPartitionsPercentage();
    return this;
  }

  /**
   * Get resource utilization stats for the cluster instance that the object was populated with.
   */
  public Map<Statistic, Map<Resource, Double>> resourceUtilizationStats() {
    return _resourceUtilizationStats;
  }

  /**
   * Get outbound network utilization stats for the cluster instance that the object was populated with.
   */
  public Map<Statistic, Double> potentialNwOutUtilizationStats() {
    return _potentialNwOutUtilizationStats;
  }

  /**
   * Get replica stats for the cluster instance that the object was populated with.
   */
  public Map<Statistic, Number> replicaStats() {
    return _replicaStats;
  }

  /**
   * Get leader replica stats for the cluster instance that the object was populated with.
   */
  public Map<Statistic, Number> leaderReplicaStats() {
    return _leaderReplicaStats;
  }

  /**
   * Get topic replica stats for the cluster instance that the object was populated with.
   */
  public Map<Statistic, Number> topicReplicaStats() {
    return _topicReplicaStats;
  }

  /**
   * Get number of brokers for the cluster instance that the object was populated with.
   */
  public int numBrokers() {
    return _numBrokers;
  }

  /**
   * Get number of replicas for the cluster instance that the object was populated with.
   */
  public int numReplicasInCluster() {
    return _numReplicasInCluster;
  }

  /**
   * Get number of number of partitions with offline replicas in the cluster.
   */
  public int numPartitionsWithOfflineReplicas() {
    return _numPartitionsWithOfflineReplicas;
  }

  /**
   * Get number of topics for the cluster instance that the object was populated with.
   */
  public int numTopics() {
    return _numTopics;
  }

  /**
   * Get number of balanced brokers by resource for the cluster instance that the object was populated with.
   */
  public Map<Resource, Integer> numBalancedBrokersByResource() {
    return _numBalancedBrokersByResource;
  }

  /**
   * Get number of brokers under potential nw out for the cluster instance that the object was populated with.
   */
  public int numBrokersUnderPotentialNwOut() {
    return _numBrokersUnderPotentialNwOut;
  }

  /**
   * This is the utilization matrix generated from {@link ClusterModel#utilizationMatrix()}.
   * @return non-null if populate has been called else this may return null.
   */
  public double[][] utilizationMatrix() {
    return _utilizationMatrix;
  }

  /**
   * Get the monitored partition percentage of this cluster model;
   */
  public double monitoredPartitionsPercentage() {
    return _monitoredPartitionsPercentage;
  }

  /**
   * Get the number of snapshot windows used by this cluster model;
   */
  public int numSnapshotWindows() {
    return _numSnapshotWindows;
  }

  /*
   * Return a valid JSON encoded string
   */
  public String getJSONString() {
    Gson gson = new Gson();
    return gson.toJson(getJsonStructure());
  }

  /*
   * Return an object that can be further used
   * to encode into JSON
   */
  public Map<String, Object> getJsonStructure() {
    Map<String, Object> statMap = new HashMap<>();
    Map<String, Integer> basicMap = new HashMap<>();
    basicMap.put(AnalyzerUtils.BROKERS, numBrokers());
    basicMap.put(AnalyzerUtils.REPLICAS, numReplicasInCluster());
    basicMap.put(AnalyzerUtils.TOPICS, numTopics());
    List<Statistic> cachedStatistic = Statistic.cachedValues();
    // List of all statistics AVG, MAX, MIN, STD
    Map<String, Object> allStatMap = new HashMap<>(cachedStatistic.size());
    for (Statistic stat : cachedStatistic) {
      List<Resource> cachedResources = Resource.cachedValues();
      Map<String, Object> resourceMap = new HashMap<>(cachedResources.size() + 3);
      for (Resource resource : cachedResources) {
        resourceMap.put(resource.resource(), resourceUtilizationStats().get(stat).get(resource));
      }
      resourceMap.put(AnalyzerUtils.POTENTIAL_NW_OUT, potentialNwOutUtilizationStats().get(stat));
      resourceMap.put(AnalyzerUtils.REPLICAS, replicaStats().get(stat));
      resourceMap.put(AnalyzerUtils.TOPIC_REPLICAS, topicReplicaStats().get(stat));
      allStatMap.put(stat.stat(), resourceMap);
    }
    statMap.put(AnalyzerUtils.METADATA, basicMap);
    statMap.put(AnalyzerUtils.STATISTICS, allStatMap);
    return statMap;
  }

  /**
   * @return A string representation of the cluster counts including brokers, replicas, and topics.
   */
  public String toStringCounts() {
    return String.format("%d brokers %d replicas %d topics.", numBrokers(), numReplicasInCluster(), numTopics());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Statistic stat : Statistic.cachedValues()) {
      sb.append(String.format("%s:{", stat));
      for (Resource resource : Resource.cachedValues()) {
        sb.append(String.format("%s:%12.3f ", resource, resourceUtilizationStats().get(stat).get(resource)));
      }
      sb.append(String.format("potentialNwOut:%12.3f replicas:%s topicReplicas:%s}%n",
                              potentialNwOutUtilizationStats().get(stat), replicaStats().get(stat),
                              topicReplicaStats().get(stat)));
    }
    return sb.substring(0, sb.length() - 2);
  }

  /**
   * Generate statistics of utilization for resources in the given cluster.
   *
   * @param clusterModel The state of the cluster.
   */
  private void utilizationForResources(ClusterModel clusterModel) {
    // Average, maximum, and standard deviation of utilization by resource.
    Map<Resource, Double> avgUtilizationByResource = new HashMap<>();
    Map<Resource, Double> maxUtilizationByResource = new HashMap<>();
    Map<Resource, Double> minUtilizationByResource = new HashMap<>();
    Map<Resource, Double> stDevUtilizationByResource = new HashMap<>();
    for (Resource resource : Resource.cachedValues()) {
      double balanceUpperThreshold = (clusterModel.load().expectedUtilizationFor(resource) / clusterModel.capacityFor(resource))
          * _balancingConstraint.resourceBalancePercentage(resource);
      double balanceLowerThreshold = (clusterModel.load().expectedUtilizationFor(resource) / clusterModel.capacityFor(resource))
          * Math.max(0, (2 - _balancingConstraint.resourceBalancePercentage(resource)));
      // Average utilization for the resource.
      double avgUtilization = clusterModel.load().expectedUtilizationFor(resource) / _numBrokers;
      avgUtilizationByResource.put(resource, avgUtilization);

      // Maximum, minimum, and standard deviation utilization for the resource.
      double hottestBrokerUtilization = 0.0;
      double coldestBrokerUtilization = Double.MAX_VALUE;
      double variance = 0.0;
      int numBalancedBrokers = 0;
      for (Broker broker : clusterModel.brokers()) {
        double utilization = resource.isHostResource() ?
            broker.host().load().expectedUtilizationFor(resource) : broker.load().expectedUtilizationFor(resource);
        double balanceUpperLimit = resource.isHostResource() ?
            broker.host().capacityFor(resource) * balanceUpperThreshold : broker.capacityFor(resource) * balanceUpperThreshold;
        double balanceLowerLimit = resource.isHostResource() ?
            broker.host().capacityFor(resource) * balanceLowerThreshold : broker.capacityFor(resource) * balanceLowerThreshold;
        if (utilization <= balanceUpperLimit && utilization >= balanceLowerLimit) {
          numBalancedBrokers++;
        }
        double brokerUtilization = broker.load().expectedUtilizationFor(resource);
        hottestBrokerUtilization = Math.max(hottestBrokerUtilization, brokerUtilization);
        coldestBrokerUtilization = Math.min(coldestBrokerUtilization, brokerUtilization);
        variance += (Math.pow(brokerUtilization - avgUtilization, 2) / _numBrokers);
      }
      _numBalancedBrokersByResource.put(resource, numBalancedBrokers);
      maxUtilizationByResource.put(resource, hottestBrokerUtilization);
      minUtilizationByResource.put(resource, coldestBrokerUtilization);
      stDevUtilizationByResource.put(resource, Math.sqrt(variance));
    }

    _resourceUtilizationStats.put(Statistic.AVG, avgUtilizationByResource);
    _resourceUtilizationStats.put(Statistic.MAX, maxUtilizationByResource);
    _resourceUtilizationStats.put(Statistic.MIN, minUtilizationByResource);
    _resourceUtilizationStats.put(Statistic.ST_DEV, stDevUtilizationByResource);
  }

  /**
   * Generate statistics of utilization for potential network outbound utilization in the given cluster.
   *
   * @param clusterModel The state of the cluster.
   */
  private void utilizationForPotentialNwOut(ClusterModel clusterModel) {
    // Average, minimum, and maximum: network outbound utilization and replicas in brokers.
    double potentialNwOutInCluster = 0.0;
    double maxPotentialNwOut = 0.0;
    double minPotentialNwOut = Double.MAX_VALUE;
    for (Broker broker : clusterModel.brokers()) {
      double capacityLimit = broker.capacityFor(Resource.NW_OUT) * _balancingConstraint.capacityThreshold(Resource.NW_OUT);

      if (clusterModel.potentialLeadershipLoadFor(broker.id()).expectedUtilizationFor(Resource.NW_OUT) <= capacityLimit) {
        _numBrokersUnderPotentialNwOut++;
      }
      double brokerUtilization = clusterModel.potentialLeadershipLoadFor(broker.id()).expectedUtilizationFor(Resource.NW_OUT);
      maxPotentialNwOut = Math.max(maxPotentialNwOut, brokerUtilization);
      minPotentialNwOut = Math.min(minPotentialNwOut, brokerUtilization);
      potentialNwOutInCluster += brokerUtilization;
    }
    double avgPotentialNwOut = potentialNwOutInCluster / _numBrokers;

    // Standard deviation of network outbound utilization.
    double varianceForPotentialNwOut = 0.0;
    for (Broker broker : clusterModel.brokers()) {
      double brokerUtilization = clusterModel.potentialLeadershipLoadFor(broker.id()).expectedUtilizationFor(Resource.NW_OUT);
      varianceForPotentialNwOut += (Math.pow(brokerUtilization - avgPotentialNwOut, 2) / _numBrokers);
    }

    _potentialNwOutUtilizationStats.put(Statistic.AVG, avgPotentialNwOut);
    _potentialNwOutUtilizationStats.put(Statistic.MAX, maxPotentialNwOut);
    _potentialNwOutUtilizationStats.put(Statistic.MIN, minPotentialNwOut);
    _potentialNwOutUtilizationStats.put(Statistic.ST_DEV, Math.sqrt(varianceForPotentialNwOut));
  }

  /**
   * Generate statistics for replicas in the given cluster.
   *
   * @param clusterModel The state of the cluster.
   */
  private void numForReplicas(ClusterModel clusterModel) {
    populateReplicaStats(clusterModel,
                         broker -> broker.replicas().size(),
                         _replicaStats);
    _numReplicasInCluster = clusterModel.numReplicas();
    // Set the number of partitions with offline replicas.
    Set<TopicPartition> partitionsWithOfflineReplicas = new HashSet<>();
    for (Replica replica : clusterModel.selfHealingEligibleReplicas()) {
      partitionsWithOfflineReplicas.add(replica.topicPartition());
    }
    _numPartitionsWithOfflineReplicas = partitionsWithOfflineReplicas.size();
  }

  /**
   * Generate statistics for leader replicas in the given cluster.
   *
   * @param clusterModel The state of the cluster.
   */
  private void numForLeaderReplicas(ClusterModel clusterModel) {
    populateReplicaStats(clusterModel,
                         broker -> broker.leaderReplicas().size(),
                         _leaderReplicaStats);
  }

  /**
   * Generate statistics for replicas of interest in the given cluster.
   *
   * @param clusterModel The state of the cluster.
   * @param numInterestedReplicasFunc function to calculate number of replicas of interest on a broker.
   * @param interestedReplicaStats statistics for replicas of interest.
   */
  private void populateReplicaStats(ClusterModel clusterModel,
                                    Function<Broker, Integer> numInterestedReplicasFunc,
                                    Map<Statistic, Number> interestedReplicaStats) {
    // Average, minimum, and maximum number of replicas of interest in brokers.
    int maxInterestedReplicasInBroker = 0;
    int minInterestedReplicasInBroker = Integer.MAX_VALUE;
    int numInterestedReplicasInCluster = 0;
    int numAliveBrokers = clusterModel.aliveBrokers().size();
    for (Broker broker : clusterModel.brokers()) {
      int numInterestedReplicasInBroker = numInterestedReplicasFunc.apply(broker);
      numInterestedReplicasInCluster += numInterestedReplicasInBroker;
      maxInterestedReplicasInBroker = Math.max(maxInterestedReplicasInBroker, numInterestedReplicasInBroker);
      minInterestedReplicasInBroker = Math.min(minInterestedReplicasInBroker, numInterestedReplicasInBroker);
    }
    double avgInterestedReplicas = ((double) numInterestedReplicasInCluster) / numAliveBrokers;

    // Standard deviation of replicas of interest in alive brokers.
    double varianceForInterestedReplicas = 0.0;
    for (Broker broker : clusterModel.aliveBrokers()) {
      varianceForInterestedReplicas +=
          (Math.pow((double) numInterestedReplicasFunc.apply(broker) - avgInterestedReplicas, 2) / numAliveBrokers);
    }

    interestedReplicaStats.put(Statistic.AVG, avgInterestedReplicas);
    interestedReplicaStats.put(Statistic.MAX, maxInterestedReplicasInBroker);
    interestedReplicaStats.put(Statistic.MIN, minInterestedReplicasInBroker);
    interestedReplicaStats.put(Statistic.ST_DEV, Math.sqrt(varianceForInterestedReplicas));
  }

  /**
   * Generate statistics for topic replicas in the given cluster.
   *
   * @param clusterModel The state of the cluster.
   */
  private void numForAvgTopicReplicas(ClusterModel clusterModel) {
    _topicReplicaStats.put(Statistic.AVG, 0.0);
    _topicReplicaStats.put(Statistic.MAX, 0);
    _topicReplicaStats.put(Statistic.MIN, Integer.MAX_VALUE);
    _topicReplicaStats.put(Statistic.ST_DEV, 0.0);
    int numAliveBrokers = clusterModel.aliveBrokers().size();
    for (String topic : clusterModel.topics()) {
      int maxTopicReplicasInBroker = 0;
      int minTopicReplicasInBroker = Integer.MAX_VALUE;
      for (Broker broker : clusterModel.brokers()) {
        int numTopicReplicasInBroker = broker.numReplicasOfTopicInBroker(topic);
        maxTopicReplicasInBroker = Math.max(maxTopicReplicasInBroker, numTopicReplicasInBroker);
        minTopicReplicasInBroker = Math.min(minTopicReplicasInBroker, numTopicReplicasInBroker);
      }
      double avgTopicReplicas = ((double) clusterModel.numTopicReplicas(topic)) / numAliveBrokers;

      // Standard deviation of replicas in alive brokers.
      double variance = 0.0;
      for (Broker broker : clusterModel.aliveBrokers()) {
        variance += (Math.pow(broker.numReplicasOfTopicInBroker(topic) - avgTopicReplicas, 2)
            / (double) numAliveBrokers);
      }

      _topicReplicaStats.put(Statistic.AVG, _topicReplicaStats.get(Statistic.AVG).doubleValue() + avgTopicReplicas);
      _topicReplicaStats.put(Statistic.MAX,
          Math.max(_topicReplicaStats.get(Statistic.MAX).intValue(), maxTopicReplicasInBroker));
      _topicReplicaStats.put(Statistic.MIN,
          Math.min(_topicReplicaStats.get(Statistic.MIN).intValue(), minTopicReplicasInBroker));
      _topicReplicaStats.put(Statistic.ST_DEV, (Double) _topicReplicaStats.get(Statistic.ST_DEV) + Math.sqrt(variance));
    }

    _topicReplicaStats.put(Statistic.AVG, _topicReplicaStats.get(Statistic.AVG).doubleValue() / _numTopics);
    _topicReplicaStats.put(Statistic.ST_DEV, _topicReplicaStats.get(Statistic.ST_DEV).doubleValue() / _numTopics);
  }
}
