# 현재 저장소의 기본 YAML + 로컬 시크릿 import에 한정한 테스트용 로더입니다.
# Spring 전체 설정 엔진(다른 프로필, 커맨드라인 옵션 등)을 재현하지 않습니다.
require 'yaml'
require 'json'

begin
  mode = ARGV.fetch(0, 'check')
  section = mode == 'webhook-secret' ? 'webhook' : 'api'
  resources = File.expand_path('../src/main/resources', __dir__)
  base = YAML.safe_load(File.read(File.join(resources, 'application.yaml'))) || {}
  imports = Array(base.dig('spring', 'config', 'import'))
  unless imports.include?('optional:classpath:application-local.yaml') ||
         imports.include?('classpath:application-local.yaml')
    abort 'application.yaml의 application-local.yaml import를 확인하세요.'
  end
  local = YAML.safe_load(File.read(File.join(resources, 'application-local.yaml'))) || {}
  # import한 설정이 기본 파일의 같은 속성을 덮어씁니다.
  value = local.dig('portone', section, 'secret')
  value = base.dig('portone', section, 'secret') if value.nil?
  # Spring의 portone.api.secret에 직접 대응하는 환경변수 우선순위.
  environment_key = section == 'webhook' ? 'PORTONE_WEBHOOK_SECRET' : 'PORTONE_API_SECRET'
  value = ENV[environment_key] if ENV.key?(environment_key)
  if value.is_a?(String) && (match = /\A\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}\z/.match(value))
    name, fallback = match.captures
    value = ENV.fetch(name) { local.fetch(name) { base.fetch(name, fallback) } }
  end
  unless value.is_a?(String) && /\A[A-Za-z0-9+\/_=-]+\z/.match?(value)
    abort '포트원 시크릿이 비었거나 지원하지 않는 형식입니다. 로컬 설정을 확인하세요.'
  end

  case mode
  when 'webhook-secret'
    # 수신 테스트 프로그램이 비공개 파이프로 읽는 용도입니다. 터미널에서 직접 실행하지 마세요.
    print value
  when 'check'
    warn '포트원 API 시크릿 설정을 읽었습니다. (값 비공개)'
  when 'header'
    puts "Authorization: PortOne #{value}"
  when 'prepare-http'
    path = File.join(__dir__, 'http-client.private.env.json')
    settings = File.exist?(path) ? JSON.parse(File.read(path)) : {}
    settings['portone-local'] ||= {}
    settings['portone-local']['PORTONE_SECRET'] = value
    File.open(path, File::WRONLY | File::CREAT | File::TRUNC, 0600) do |file|
      file.chmod(0600)
      file.write(JSON.pretty_generate(settings) + "\n")
    end
    puts 'HTTP Client 설정 준비 완료. portone-local 환경을 선택하세요. 키 변경 시 다시 실행하세요.'
  else
    abort '지원하지 않는 실행 모드입니다.'
  end
rescue StandardError => error
  # YAML 파서 오류에는 원문 시크릿이 포함될 수 있으므로 상세 메시지는 출력하지 않습니다.
  abort "설정 파일을 읽지 못했습니다 (#{error.class}). YAML/JSON 형식과 파일 권한을 확인하세요."
end
