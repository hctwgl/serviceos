const regionNames: Record<string, string> = {
  '370000': '山东省',
  '370100': '济南市',
  '370102': '济南市历下区',
  '370103': '济南市市中区',
  '370104': '济南市槐荫区',
  '370105': '济南市天桥区',
  '370112': '济南市历城区',
  '370114': '济南市章丘区',
}

const qualificationNames: Record<string, string> = {
  HOME_CHARGING_INSTALLATION: '家用充电设施安装资质',
}

export function presentRegions(codes: string[]) {
  return codes.map((code) => regionNames[code] ?? '区域名称缺失')
}

export function presentQualifications(codes: string[]) {
  return codes.map((code) => qualificationNames[code] ?? '资质名称缺失')
}

export function presentClientKinds(kinds: string[]) {
  return kinds.map((kind) => kind === 'TECHNICIAN_IOS' ? 'iOS 现场作业端' : '在线师傅端')
}
