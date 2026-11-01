"""Fixed 770 graph export; preserves learned weights except positional interpolation.
Position interpolation follows DepthAnything/Depth-Anything-V2 (Apache-2.0), based on Meta DINOv2.
No source media or learned weight files are included in this script."""
from pathlib import Path
import argparse
import importlib.util,json,hashlib
import numpy as np,torch,flatbuffers
parser=argparse.ArgumentParser(description="Retarget the verified original Depth Anything V2 Small TFLite graph to 770. Requires torch, numpy, flatbuffers and a TensorFlow schema module.")
parser.add_argument('--input',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--schema',type=Path,required=True,help='tensorflow/lite/python/schema_py_generated.py from TensorFlow 2.20')
args=parser.parse_args()
if args.output.exists():raise FileExistsError(args.output)
if args.input.resolve()==args.output.resolve():raise ValueError('Keep the original model intact')
schema=args.schema
b=args.output.parent
b.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('schema',schema);s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
source=args.input
assert hashlib.sha256(source.read_bytes()).hexdigest()=='b407f34f61750f31441e6f858a4bc48d8572f9ee5399ffd015cee5fa1767083f', 'Unexpected source model'
raw=source.read_bytes();m=s.ModelT.InitFromObj(s.Model.GetRootAsModel(raw,0));g=m.subgraphs[0]
size=770;n=size//14
mapping={518:size,37:n,19:(n+1)//2,74:n*2,148:n*4,296:n*8,1369:n*n,1370:n*n+1}
changes=[]
for idx,t in enumerate(g.tensors):
 buf=m.buffers[t.buffer];data=buf.data
 if data is not None and len(data):
  if t.type==s.TensorType.INT32:
   vals=np.frombuffer(data.tobytes(),dtype='<i4').copy();new=np.array([mapping.get(int(v),int(v)) for v in vals],dtype='<i4')
   if not np.array_equal(vals,new):
    changes.append({'tensor':idx,'kind':'shape-constant','before':vals.tolist(),'after':new.tolist()});buf.data=np.frombuffer(new.tobytes(),np.uint8)
  elif list(t.shape)==[1,1370,384]:
   pos=torch.from_numpy(np.frombuffer(data.tobytes(),dtype='<f4').copy().reshape(1,1370,384))
   patches=torch.nn.functional.interpolate(pos[:,1:].reshape(1,37,37,384).permute(0,3,1,2),scale_factor=((n+.1)/37,(n+.1)/37),mode='bicubic',align_corners=False,antialias=False)
   new=torch.cat([pos[:,:1],patches.permute(0,2,3,1).reshape(1,n*n,384)],dim=1).numpy()
   buf.data=np.frombuffer(new.astype('<f4').tobytes(),np.uint8);t.shape=np.array([1,n*n+1,384],np.int32)
   changes.append({'tensor':idx,'kind':'official-position-interpolation','after':t.shape.tolist()})
 else:
  if t.shape is not None:t.shape=np.array([mapping.get(int(v),int(v)) for v in t.shape],np.int32)
  if t.shapeSignature is not None:t.shapeSignature=np.array([mapping.get(int(v),int(v)) for v in t.shapeSignature],np.int32)
for op in g.operators:
 opt=op.builtinOptions
 if isinstance(opt,s.ReshapeOptionsT) and opt.newShape is not None:opt.newShape=np.array([mapping.get(int(v),int(v)) for v in opt.newShape],np.int32)
builder=flatbuffers.Builder(128*1024*1024);offset=m.Pack(builder);builder.Finish(offset,file_identifier=b'TFL3')
out=args.output;out.write_bytes(builder.Output())
out.with_suffix('.manifest.json').write_text(json.dumps({'sourceSha256':hashlib.sha256(raw).hexdigest(),'outputSha256':hashlib.sha256(out.read_bytes()).hexdigest(),'changes':changes,'mapping':mapping},indent=2))
print('Created research graph',out.name,len(out.read_bytes()),'changes',len(changes))
