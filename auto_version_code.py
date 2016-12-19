import xml.etree.ElementTree as etree
from datetime import datetime, date, time

namespace = 'http://schemas.android.com/apk/res/android'
name = '{' + namespace + '}versionCode'

d = date(2012, 8, 12)
t = time(21, 59)
start = datetime.combine(d, t)
#print start

now = datetime.now()
diff = now - start
versionCode = '{:d}'.format(diff.days)
#print versionCode

etree.register_namespace('android', namespace)
with open('AndroidManifest.xml', 'r') as handle:
    tree = etree.parse(handle)

root = tree.getroot()
root.attrib[name] = versionCode

tree.write('AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
