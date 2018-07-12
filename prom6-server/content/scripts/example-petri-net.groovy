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

// newFile is a shortcut of new File("${TMP_DIR}/${PROM_REQUEST_ID}/<file>") and adding it to output list
prom('pnml_export_petri_net_', net, newFile("mined_net.pnml"))

return 2333