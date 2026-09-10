import Image from "next/image";
import Link from "next/link";
import { CLINIC, BOOKING_STEPS, COMMITMENTS, REASONS, SERVICES } from "@/config/clinic";
import { ButtonLink, Container, Section, SectionHead } from "@/components/site/primitives";
import { FeaturedProducts } from "@/components/site/FeaturedProducts";
import { DoctorTeam } from "@/components/site/DoctorTeam";

export default function HomePage() {
  return (
    <>
      <Hero />
      <BrandStory />
      <Services />
      <WhyUs />
      <DoctorTeam />
      <FeaturedProducts />
      <HowToBook />
      <Commitments />
      <ClosingCta />
    </>
  );
}

/**
 * Mở đầu bằng thứ đặc trưng nhất của thế giới này: con vật, nhìn thẳng vào người xem.
 * Không mở bằng một con số to kèm nhãn nhỏ — đó là cách mặc định và nó nói về công ty,
 * trong khi thứ khách quan tâm là con vật của họ.
 *
 * Mái vòm tràn xuống dải kế tiếp, nên dải này để `pb` nhỏ và cho vòm chồm ra ngoài.
 */
function Hero() {
  return (
    <div className="relative z-10 bg-white pb-8 pt-10 md:pb-0 md:pt-16">
      <Container>
        {/*
          Trên máy để bàn: chữ bên trái, vòm ảnh bên phải trải suốt hai hàng.
          Trên điện thoại: chữ, rồi ảnh, rồi mới đến dải thông tin — để con vật xuất hiện
          sớm chứ không bị đẩy xuống dưới ba khối chữ.
        */}
        <div className="grid items-center gap-10 md:grid-cols-[1.05fr_0.95fr] md:grid-rows-[auto_auto] md:gap-x-14 md:gap-y-8">
          <div className="md:col-start-1 md:row-start-1">
            <h1 className="t-display enter text-pine">Bé nhà bạn xứng đáng được khám tử tế.</h1>

            <p
              className="measure t-lede enter mt-6 text-stone"
              style={{ animationDelay: "90ms" }}
            >
              Phòng khám thú y cho chó, mèo và thú cưng nhỏ. Bác sĩ có chứng chỉ hành nghề, mỗi
              lần khám đều lưu thành bệnh án, và bạn đặt lịch xong trong khoảng một phút.
            </p>

            <div
              className="enter mt-9 flex flex-wrap gap-3"
              style={{ animationDelay: "180ms" }}
            >
              <ButtonLink href="/appointments/create" size="lg">
                Đặt lịch khám
              </ButtonLink>
              <ButtonLink href="/products" size="lg" variant="outline">
                Xem sản phẩm
              </ButtonLink>
            </div>
          </div>

          {/*
            Vòm chồm xuống dải sau bằng margin âm. Rẻ, mà làm cả trang khác hẳn một trang
            chia dải đều tăm tắp.
          */}
          <div className="relative md:col-start-2 md:row-span-2 md:row-start-1 md:-mb-20 lg:-mb-28">
            <span
              aria-hidden
              className="absolute -right-6 -top-6 -z-10 hidden size-64 rounded-full bg-peach md:block"
            />
            <div className="enter-arch arch relative aspect-[4/5] w-full max-w-[26rem] md:ml-auto">
              <Image
                src="/img/hero.jpg"
                alt="Một chú chó beagle ngồi nhìn thẳng vào ống kính"
                fill
                priority
                sizes="(max-width: 768px) 92vw, 40vw"
                className="object-cover"
              />
            </div>
          </div>

          <dl
            className="enter grid max-w-lg grid-cols-2 gap-px overflow-hidden rounded-2xl bg-mist sm:grid-cols-3 md:col-start-1 md:row-start-2"
            style={{ animationDelay: "260ms" }}
          >
            <Fact label="Mở cửa" value="7:30 – 20:00" note="cả Chủ Nhật" />
            <Fact label="Gọi trước" value={CLINIC.phone} />
            <Fact
              label="Phòng khám"
              value="Cần Thơ"
              note="128 Nguyễn Văn Cừ"
              className="col-span-2 sm:col-span-1"
            />
          </dl>
        </div>
      </Container>
    </div>
  );
}

function Fact({
  label,
  value,
  note,
  className,
}: {
  label: string;
  value: string;
  note?: string;
  className?: string;
}) {
  return (
    <div className={`bg-white px-4 py-3.5 ${className ?? ""}`}>
      <dt className="text-sm text-stone">{label}</dt>
      <dd className="mt-0.5 font-medium text-pine">{value}</dd>
      {note && <p className="text-sm text-stone">{note}</p>}
    </div>
  );
}

function BrandStory() {
  return (
    <Section tone="mint" className="pt-28 md:pt-40">
      <div className="grid items-start gap-12 md:grid-cols-2 md:gap-16">
        <div>
          <h2 className="t-h2">Một phòng khám nhỏ, làm kỹ.</h2>
          <div className="measure t-body mt-6 space-y-4 text-stone">
            <p>
              Chúng tôi mở phòng khám vì đã quá nhiều lần thấy chủ nuôi bế con vật đi lòng vòng
              mà không ai giải thích cho họ chuyện gì đang xảy ra. Nên ở đây, việc đầu tiên bác sĩ
              làm không phải là kê đơn, mà là nói cho bạn hiểu bé đang bị gì.
            </p>
            <p>
              Phòng khám nhận số lượng ca vừa phải để mỗi ca có đủ thời gian. Bạn sẽ không bị hẹn
              lúc chín giờ rồi ngồi chờ đến mười một giờ.
            </p>
          </div>

          <ul className="mt-8 space-y-4">
            {[
              ["Nói trước, làm sau", "Bạn biết bé sắp được làm gì và hết bao nhiêu trước khi đồng ý."],
              ["Nhẹ tay với con vật", "Không trói, không ép. Bé sợ quá thì dừng, hẹn hôm khác."],
              ["Theo đến khi khỏi", "Về nhà rồi vẫn gọi hỏi được, không tính là lượt khám mới."],
            ].map(([title, body]) => (
              <li key={title} className="border-l-2 border-coral pl-4">
                <p className="font-medium text-pine">{title}</p>
                <p className="text-stone">{body}</p>
              </li>
            ))}
          </ul>
        </div>

        <div className="arch relative aspect-[4/5] w-full max-w-[24rem] md:ml-auto">
          <Image
            src="/img/cham-soc.jpg"
            alt="Bàn tay đang vuốt ve một con mèo"
            fill
            sizes="(max-width: 768px) 92vw, 36vw"
            className="object-cover"
          />
        </div>
      </div>
    </Section>
  );
}

/**
 * Dịch vụ trình bày thành hàng có đường kẻ ngăn, không phải lưới thẻ giống hệt nhau —
 * bốn cái thẻ bo góc như nhau là dấu hiệu rõ nhất của giao diện dựng bằng khuôn mẫu.
 */
function Services() {
  return (
    <Section id="dich-vu" tone="white">
      <SectionHead
        title="Bé cần gì, phòng khám làm được gì"
        lede="Bốn nhóm việc chúng tôi làm hằng ngày. Ca phức tạp hơn thì bác sĩ hội chẩn rồi báo bạn hướng xử lý."
      />

      <ul className="grid gap-x-14 sm:grid-cols-2">
        {SERVICES.map((s) => (
          <li
            key={s.name}
            className="flex gap-5 border-t border-mist py-8 first:border-t-0 sm:[&:nth-child(2)]:border-t-0"
          >
            <span
              aria-hidden
              className="arch-sm grid size-14 shrink-0 place-items-center bg-mint text-teal"
            >
              <s.icon className="size-6" />
            </span>
            <div className="min-w-0">
              <h3 className="t-h3">{s.name}</h3>
              <p className="mt-2 text-stone">{s.description}</p>
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function WhyUs() {
  const [lead, ...rest] = REASONS;

  return (
    <Section tone="pine">
      <div className="grid gap-12 lg:grid-cols-[1.1fr_1fr] lg:gap-20">
        <div>
          <h2 className="t-h2">Vì sao gửi bé ở đây</h2>
          <p className="measure t-lede mt-5 text-white/75">
            Ba điều dưới đây kiểm chứng được, không phải khẩu hiệu treo tường.
          </p>

          <div className="mt-10 rounded-[var(--radius-card)] bg-white/8 p-7">
            <h3 className="t-h3">{lead.title}</h3>
            <p className="measure mt-3 text-white/75">{lead.body}</p>
          </div>
        </div>

        <ul className="space-y-8 lg:pt-24">
          {rest.map((r) => (
            <li key={r.title} className="border-t border-white/15 pt-8 first:border-t-0 first:pt-0">
              <h3 className="t-h3">{r.title}</h3>
              <p className="measure mt-3 text-white/70">{r.body}</p>
            </li>
          ))}
        </ul>
      </div>
    </Section>
  );
}

function HowToBook() {
  return (
    <Section tone="white">
      <SectionHead
        title="Đặt lịch thế nào"
        lede="Bốn bước, làm trên điện thoại cũng được. Không cần gọi điện nếu bạn không muốn."
        action={
          <ButtonLink href="/appointments/create" variant="outline">
            Bắt đầu đặt lịch
          </ButtonLink>
        }
      />

      <ol className="grid gap-x-10 gap-y-10 sm:grid-cols-2 lg:grid-cols-4">
        {BOOKING_STEPS.map((step, i) => (
          <li key={step.title}>
            <span
              aria-hidden
              className="grid size-11 place-items-center rounded-full bg-teal font-[family-name:var(--font-brand)] text-[19px] font-semibold text-white"
            >
              {i + 1}
            </span>
            <h3 className="mt-5 font-[family-name:var(--font-brand)] text-[19px] font-semibold">
              {step.title}
            </h3>
            <p className="mt-2 text-stone">{step.body}</p>
          </li>
        ))}
      </ol>
    </Section>
  );
}

/*
  Chỗ này ở một trang thương mại thường là lời khen của khách. Backend chưa có API đánh
  giá, nên viết sẵn mấy câu khen kèm tên người là dựng chuyện. Thay bằng cam kết của
  chính phòng khám: vẫn tạo niềm tin mà không bịa ra người không có thật.
*/
function Commitments() {
  return (
    <Section tone="mint">
      <SectionHead
        title="Chúng tôi cam kết"
        lede="Đây là lời của phòng khám, không phải lời khen mượn của ai. Bạn giữ lấy mà đối chiếu."
      />

      <ul className="grid gap-10 md:grid-cols-3">
        {COMMITMENTS.map((c) => (
          <li key={c.title}>
            <span aria-hidden className="block h-1 w-10 rounded-full bg-coral" />
            <h3 className="t-h3 mt-5">{c.title}</h3>
            <p className="mt-3 text-stone">{c.body}</p>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function ClosingCta() {
  return (
    <Section tone="peach">
      <div className="flex flex-col items-center text-center">
        <div className="arch relative aspect-[3/4] w-56">
          <Image
            src="/img/cho-con.jpg"
            alt="Một chú chó con nằm cuộn tròn"
            fill
            sizes="14rem"
            className="object-cover"
          />
        </div>

        <h2 className="t-h2 mt-10 max-w-[16ch]">Hôm nay bé thế nào?</h2>
        <p className="measure t-lede mt-5 text-stone">
          Nếu bé có gì khác thường, đừng đợi xem sao. Đặt một lịch khám, mất vài phút, và bạn
          yên tâm hơn nhiều.
        </p>

        <div className="mt-9 flex flex-wrap justify-center gap-3">
          <ButtonLink href="/appointments/create" size="lg">
            Đặt lịch khám
          </ButtonLink>
          <a
            href={`tel:${CLINIC.phone.replace(/\s/g, "")}`}
            className="inline-flex h-14 items-center justify-center rounded-full border border-mist bg-white px-8 text-[17px] font-medium text-pine transition-colors hover:border-teal"
          >
            Gọi {CLINIC.phone}
          </a>
        </div>

        <p className="mt-6 text-sm text-stone">
          Chưa có tài khoản?{" "}
          <Link href="/register" className="text-teal-deep underline underline-offset-4">
            Đăng ký trong một phút
          </Link>
        </p>
      </div>
    </Section>
  );
}
