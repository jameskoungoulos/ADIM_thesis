import sys
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from scipy.misc.pilutil import imread

filename = ''
if len(sys.argv) == 1:
	filename = 'MIRAMAR_TPP_SOLO_LONG_LOWLOD_HITREGO_CITYCOMBAT.txt'
else:
	filename = sys.argv[1]+'.txt'

# Default is miramar because the default pcap file is miramar in the kotlin program
imgname = 'Miramar_stats.bmp'
if filename.split('_')[0] == 'ERANGEL':
	imgname = 'Erangel_stats.bmp'

## read x, y coords from data file
x = []
y = []
time = []
file = open(filename, "r")

for record in file:
	contents = record.split(", ",1)
	time.append(int(contents[0]))
	data = contents[1].strip('[]').split(',')
	x.append(float(data[0]))
	y.append(float(data[1]))
file.close()

fig = plt.figure()
#fig.set_size_inches(9, 9)

ax = fig.add_subplot(111)
ax.set_xlim(0, 819200)
ax.set_ylim(819200, 0)
plt.axis("off")

img = imread(imgname)
plt.imshow(img, zorder=0, extent=[0, 819200, 0, 819200])

## first line
plt.plot([x[0],x[1]],[y[0],y[1]], color='black')

ox = x[1]
oy = y[1]

del(time[0])
del(time[1])
del(x[0])
del(x[1])
del(y[0])
del(y[1])

for timestamp, cx, cy in zip(time,x,y):
	## lable and colour lines depending on auxiliary data
	plt.plot([ox,cx],[oy,cy], color='black')
	ox = cx
	oy = cy

#plt.savefig(filename.split('.')[0]+'.png', dpi = 900)

plt.show()
