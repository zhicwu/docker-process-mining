//
// Example copied from:
// https://dirksmetric.wordpress.com/2015/03/11/tutorial-automating-process-mining-with-proms-command-line-interface/
//
import org.deckfour.xes.classification.XEventNameClassifier

import org.processmining.alphaminer.parameters.AlphaMinerParameters
import org.processmining.alphaminer.parameters.AlphaVersion

def log = prom('open_xes_log_file', 'example-logs/repairExample.xes')

println("Mining model")
def net_and_marking = prom('alpha_miner', log,
    new XEventNameClassifier(), new AlphaMinerParameters(AlphaVersion.CLASSIC))
def net = net_and_marking[0]
def marking = net_and_marking[1]

println("Saving net")
File net_file = new File("/tmp/mined_net.pnml")
prom('pnml_export_petri_net_', net, net_file)

return 2333