import org.deckfour.xes.classification.XEventNameClassifier

import org.processmining.framework.packages.PackageManager.Canceller

import org.processmining.plugins.InductiveMiner.mining.logs.LifeCycleClassifier
import org.processmining.plugins.InductiveMiner.plugins.EfficientTreeExportPlugin

import org.processmining.plugins.inductiveminer2.logs.IMLogImpl

import org.processmining.plugins.inductiveminer2.mining.MiningParameters
import org.processmining.plugins.inductiveminer2.mining.InductiveMiner

import org.processmining.plugins.inductiveminer2.plugins.ProcessTreeVisualisation

import org.processmining.plugins.inductiveminer2.variants.MiningParametersIM
import org.processmining.plugins.inductiveminer2.variants.MiningParametersIMInfrequent
import org.processmining.plugins.inductiveminer2.variants.MiningParametersIMInfrequentLifeCycle
import org.processmining.plugins.inductiveminer2.variants.MiningParametersIMInfrequentPartialTraces
import org.processmining.plugins.inductiveminer2.variants.MiningParametersIMInfrequentPartialTracesAli
import org.processmining.plugins.inductiveminer2.variants.MiningParametersIMLifeCycle
import org.processmining.plugins.inductiveminer2.variants.MiningParametersIMPartialTraces

def log = prom('open_xes_log_file', 'example-logs/repairExample.xes')

def inductiveMinerVariant = new MiningParametersIMInfrequentLifeCycle()
def miningParameters = inductiveMinerVariant.getMiningParameters()

def classifier = new XEventNameClassifier()

def imLog = new IMLogImpl(log, classifier, new LifeCycleClassifier())

miningParameters.setClassifier(classifier)
miningParameters.setNoiseThreshold(0.0f)

def efficientTree = InductiveMiner.mineEfficientTree(imLog, miningParameters, [ isCancelled: { return false } ] as Canceller)

EfficientTreeExportPlugin.export(efficientTree, newFile('efficient-tree.txt'))

def dotGraph = ProcessTreeVisualisation.fancy(efficientTree)

dotGraph.exportToFile(newFile('efficient-tree.dot'))